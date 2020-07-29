package io.fairspace.saturn.rdf.search;

import org.apache.jena.graph.Node;
import org.apache.jena.query.text.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.Var;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.google.common.collect.Iterables.partition;
import static java.lang.Thread.currentThread;
import static org.apache.jena.query.text.TextQueryFuncs.stringToNode;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Bulk Elastic Search Implementation of {@link TextIndex}
 */
public class TextIndexESBulk implements TextIndex {
    public static final int MAX_RESULTS = 10000;
    /**
     * ES Script for adding/updating the document in the index.
     * The main reason to use scripts is because we want to modify the values of the fields that contains an array of values
     */
    private static final String ADD_UPDATE_SCRIPT = "if((ctx._source == null) || (ctx._source[params.field] == null) || ctx._source[params.field].empty) " +
            "{ctx._source[params.field]=[params.value]} else {ctx._source[params.field].add(params.value)}";

    /**
     * ES Script for deleting a specific value in the field for the given document in the index.
     * The main reason to use scripts is because we want to delete specific value of the field that contains an array of values
     */
    private static final String DELETE_SCRIPT = "if((ctx._source != null) && (ctx._source[params.field] != null) && (!ctx._source[params.field].empty) " +
            "&& (ctx._source[params.field].indexOf(params.value) >= 0)) " +
            "{ctx._source[params.field].remove(ctx._source[params.field].indexOf(params.value))}";

    private static final Logger LOGGER = LoggerFactory.getLogger(TextIndexESBulk.class);
    private static final int BULK_SIZE = 1000;

    private final EntityDefinition docDef;
    private final Client client;
    private final IndexDispatcher indexDispatcher;
    private final List<UpdateRequest> updates = new ArrayList<>();


    public TextIndexESBulk(TextIndexConfig config, Client client, IndexDispatcher indexDispatcher) {
        this.docDef = config.getEntDef();
        this.client = client;
        this.indexDispatcher = indexDispatcher;
    }

    @Override
    public Map<String, Node> get(String uri) {
        // Not used
        throw new UnsupportedOperationException("TextIndex::get");
    }

    @Override
    public List<TextHit> query(Node property, String qs, String graphURI, String lang, int limit) {
        if (limit < 0)
            limit = MAX_RESULTS;

        if (property != null) {
            qs = parse(getDocDef().getField(property), qs, lang);
        } else {
            qs = parse(null, qs, lang);
        }

        LOGGER.debug("Querying ElasticSearch for QueryString: " + qs);

        var results = new ArrayList<TextHit>();

        var response = client.prepareSearch(indexDispatcher.getAvailableIndexes())
                .setTypes(getDocDef().getEntityField())
                .setQuery(QueryBuilders.queryStringQuery(qs))
                // Not fetching the source because we are currently not interested
                // in the actual values but only Id of the document. This will also speed up search
                .setFetchSource(false)
                .setFrom(0)
                .setSize(limit)
                .get();

        for (var hit : response.getHits()) {
            results.add(new TextHit(stringToNode(hit.getId()), hit.getScore(), null));
        }

        return results;
    }

    @Override
    public List<TextHit> query(Node property, String qs, String graphURI, String lang) {
        return query(property, qs, graphURI, lang, MAX_RESULTS);
    }

    @Override
    public List<TextHit> query(Node property, String qs, String graphURI, String lang, int limit, String highlight) {
        return query(property, qs, graphURI, lang, limit);
    }

    @Override
    public List<TextHit> query(List<Resource> props, String qs, String graphURI, String lang, int limit, String highlight) {
        return query((String) null, props, qs, graphURI, lang, limit, highlight);
    }

    @Override
    public List<TextHit> query(Node subj, List<Resource> props, String qs, String graphURI, String lang, int limit, String highlight) {
        var subjectUri = subj == null || Var.isVar(subj) || !subj.isURI() ? null : subj.getURI();
        return query(subjectUri, props, qs, graphURI, lang, limit, highlight);
    }

    @Override
    public List<TextHit> query(String uri, List<Resource> props, String qs, String graphURI, String lang, int limit, String highlight) {
        var property = props == null || props.isEmpty() ? null : props.get(0).asNode();
        return query(property, qs, graphURI, lang, limit);
    }

    @Override
    public EntityDefinition getDocDef() {
        return docDef;
    }

    private String parse(String fieldName, String qs, String lang) {
        if (fieldName != null && !fieldName.isEmpty()) {
            if (lang != null && !lang.equals("none")) {
                if (!"*".equals(lang)) {
                    fieldName = fieldName + "_" + lang.replaceAll("-", "_");
                } else {
                    fieldName = fieldName + "*";
                }
            }

            qs = fieldName + ":" + qs;
        }
        return qs;
    }

    @Override
    public void prepareCommit() {
        if (updates.isEmpty()) {
            return;
        }

        try {
            for (var bulk : partition(updates, BULK_SIZE)) {
                var bulkRequest = new BulkRequest();
                bulk.forEach(bulkRequest::add);
                BulkResponse response;
                try {
                    response = client.bulk(bulkRequest).get();
                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    LOGGER.error("Error indexing in ElasticSearch", e.getCause());
                    throw new RuntimeException(e.getCause());
                }
                LOGGER.debug("Indexing {} updates in ElasticSearch took {} ms",
                        response.getItems().length,
                        response.getIngestTook().millis() + response.getTook().millis());
                if (response.hasFailures()) {
                    LOGGER.error(response.buildFailureMessage());

                    throw new RuntimeException(response.buildFailureMessage());
                }
            }
        } finally {
            updates.clear();
        }
    }

    @Override
    public void commit() {
    }

    @Override
    public void rollback() {
        updates.clear();
    }

    @Override
    public void close() {
        updates.clear();
    }

    /**
     * Update an Entity. Since we are doing Upserts in add entity anyway, we simply call {@link #addEntity(Entity)}
     * method that takes care of updating the Entity as well.
     *
     * @param entity the entity to update.
     */
    @Override
    public void updateEntity(Entity entity) {
        //Since Add entity also updates the indexed document in case it already exists,
        // we can simply call the addEntity from here.
        addEntity(entity);
    }


    /**
     * Add an Entity to the ElasticSearch Index.
     * The entity will be added as a new document in ES, if it does not already exists.
     * If the Entity exists, then the entity will simply be updated.
     * The entity will never be replaced.
     *
     * @param entity the entity to add
     */
    @Override
    public void addEntity(Entity entity) {
        LOGGER.trace("Adding/Updating the entity {} in ES", entity.getId());

        var indexName = indexDispatcher.getIndex(entity.getId());
        try {
            var entry = getDataEntry(entity);
            var builder = jsonBuilder()
                    .startObject()
                    .field(getDocDef().getEntityField(), entity.getId())
                    .field(entry.getKey(), List.of(entry.getValue()))
                    .endObject();
            var indexRequest = new IndexRequest(indexName, getDocDef().getEntityField(), entity.getId())
                    .source(builder);
            var upReq = new UpdateRequest(indexName, getDocDef().getEntityField(), entity.getId())
                    .script(new Script(Script.DEFAULT_SCRIPT_TYPE, Script.DEFAULT_SCRIPT_LANG, ADD_UPDATE_SCRIPT, toParams(entity)))
                    .upsert(indexRequest);
            updates.add(upReq);
        } catch (Exception e) {
            LOGGER.error("Unable to Index the Entity in ElasticSearch.", e);
        }
    }

    /**
     * Delete the value of the entity from the existing document, if any.
     * The document itself will never get deleted. Only the value will get deleted.
     *
     * @param entity entity whose value needs to be deleted
     */
    @Override
    public void deleteEntity(Entity entity) {
        updates.add(new UpdateRequest(indexDispatcher.getIndex(entity.getId()), getDocDef().getEntityField(), entity.getId())
                .script(new Script(Script.DEFAULT_SCRIPT_TYPE, Script.DEFAULT_SCRIPT_LANG, DELETE_SCRIPT, toParams(entity))));
    }

    private static Map<String, Object> toParams(Entity entity) {
        var entry = getDataEntry(entity);
        return Map.of("field", entry.getKey(), "value", entry.getValue());
    }

    private static Map.Entry<String, Object> getDataEntry(Entity entity) {
        return entity.getMap().entrySet().iterator().next();
    }
}
