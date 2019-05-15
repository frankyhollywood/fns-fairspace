package io.fairspace.saturn.services.metadata;

import io.fairspace.saturn.services.metadata.validation.MetadataRequestValidator;
import io.fairspace.saturn.services.metadata.validation.ValidationException;
import io.fairspace.saturn.services.metadata.validation.Violation;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.modify.request.QuadDataAcc;
import org.apache.jena.sparql.modify.request.UpdateDataDelete;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.fairspace.saturn.rdf.TransactionUtils.commit;
import static io.fairspace.saturn.vocabulary.Vocabularies.getInverse;
import static java.util.stream.Collectors.toList;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

public class ChangeableMetadataService extends ReadableMetadataService {
    static final Resource NIL = createResource("http://fairspace.io/ontology#nil");
    private static final Model EMPTY = createDefaultModel();

    private final MetadataEntityLifeCycleManager lifeCycleManager;
    private final MetadataRequestValidator validator;

    /**
     * Keep a cache of the inverse properties during a transaction, in order to avoid
     * too many lookups. As many properties do not have an inverse, this map stores
     * an Optional. An empty optional indicates that we know there is no inverse for this
     * property. A missing value indicates that we do not know whether there is an inverse
     * for this prperty
     */
    private final Map<String, Optional<Property>> inverseCache = new HashMap<>();

    public ChangeableMetadataService(RDFConnection rdf, Node graph, Node vocabulary, MetadataEntityLifeCycleManager lifeCycleManager, MetadataRequestValidator validator) {
        super(rdf, graph, vocabulary);
        this.lifeCycleManager = lifeCycleManager;
        this.validator = validator;
    }

    /**
     * Adds all the statements in the given model to the database
     * <p>
     * If the given model contains any statements for which the predicate is marked as machineOnly,
     * an IllegalArgumentException will be thrown
     *
     * @param model
     */
    void put(Model model) {
        commit("Store metadata", rdf, () -> update(EMPTY, model));
    }

    /**
     * Deletes the statements in the database, based on the combination of subject, predicate and object
     * <p>
     * If any of the fields is null, that field is not included to filter statements to delete. For example, if only
     * subject is given and predicate and object are null, then all statements with the given subject will be deleted.
     * <p>
     * If the set of triples matching the provided wildcard includes any protected triple (e.g. with a predicate marked
     * as fs:machineOnly) a ValidationException will be thrown.
     *
     * @param subject   Subject URI to filter the delete query on
     * @param predicate Predicate URI to filter the delete query on. Must not be a machineOnly predicate
     * @param object    Object URI to filter the delete query on. Literal values are not supported
     */
    void delete(String subject, String predicate, String object) {
        commit("Delete metadata", rdf, () -> delete(get(subject, predicate, object, false)));
    }

    /**
     * Deletes the statements in the given model from the database.
     * <p>
     * If the model contains any statements for which the predicate is marked as 'machineOnly', an IllegalArgumentException will be thrown.
     *
     * @param model
     */
    void delete(Model model) {
        commit("Delete metadata", rdf, () -> update(model, EMPTY));
    }

    /**
     * Overwrites metadata in the database with statements from the given model.
     * <p>
     * For any subject/predicate combination in the model to add, the existing data in the database will be removed,
     * before adding the new data. This means that if the given model contains a triple
     * S rdfs:label "test"
     * then any statement in the database specifying the rdfs:label for S will be deleted. This effectively overwrites
     * values in the database.
     * <p>
     * If the given model contains any statements for which the predicate is marked as machineOnly,
     * an IllegalArgumentException will be thrown
     *
     * @param model
     */
    void patch(Model model) {
        commit("Update metadata", rdf, () -> {
            var toDelete = createDefaultModel();
            model.listStatements().forEachRemaining(stmt -> {
                // Only explicitly delete triples for URI resources. As this model is also used
                // for validation, we do not want to include blank nodes here. Triples for blank
                // nodes will be deleted automatically when it is not referred to anymore
                if (stmt.getSubject().isURIResource()) {
                    toDelete.add(get(stmt.getSubject().getURI(), stmt.getPredicate().getURI(), null, false));
                }
            });

            update(toDelete, model);
        });
    }

    private void update(Model modelToRemove, Model modelToAdd) {
        // Exclude statements already present in the database from validation
        var unchanged = modelToRemove.intersection(modelToAdd);
        modelToRemove.remove(unchanged);
        modelToAdd.remove(unchanged);
        modelToAdd.removeAll(null, null, NIL);

        // Clear inverse cache before applying inference
        // to ensure we retrieve the latest data
        inverseCache.clear();

        applyInference(modelToRemove);
        applyInference(modelToAdd);

        var violations = new LinkedHashSet<Violation>();
        validator.validate(modelToRemove, modelToAdd,
                (message, subject, predicate, object) ->
                        violations.add(new Violation(message, subject.toString(), Objects.toString(predicate, null), Objects.toString(object, null))));

        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }

        rdf.update(new UpdateDataDelete(new QuadDataAcc(toQuads(modelToRemove.listStatements().toList()))));

        // Store information on the lifecycle of the entities
        lifeCycleManager.updateLifecycleMetadata(modelToAdd);

        // Store the actual update
        rdf.load(graph.getURI(), modelToAdd);
    }

    private List<Quad> toQuads(Collection<Statement> statements) {
        return statements
                .stream()
                .map(s -> new Quad(graph, s.asTriple()))
                .collect(toList());
    }

    private void applyInference(Model model) {
        var toAdd = createDefaultModel();

        model.listStatements().forEachRemaining(stmt -> {
            if (stmt.getObject().isResource()) {
                getInverseWithCache(stmt.getPredicate())
                        .ifPresent(inverse ->
                                toAdd.add(stmt.getObject().asResource(), inverse, stmt.getSubject())
                        );
            }
        });

        model.add(toAdd);
    }

    private Optional<Property> getInverseWithCache(Property property) {
        return inverseCache.computeIfAbsent(
                property.getURI(),
                s -> Optional.ofNullable(getInverse(rdf, vocabulary, property))
        );
    }

}
