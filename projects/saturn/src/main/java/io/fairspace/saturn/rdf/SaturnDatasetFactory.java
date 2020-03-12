package io.fairspace.saturn.rdf;

import io.fairspace.saturn.config.Config;
import io.fairspace.saturn.rdf.search.*;
import io.fairspace.saturn.rdf.transactions.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.dboe.base.file.Location;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.sparql.core.DatasetGraph;
import org.elasticsearch.client.Client;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;

import static io.fairspace.saturn.rdf.MarkdownDataType.MARKDOWN_DATA_TYPE;
import static io.fairspace.saturn.rdf.transactions.Restore.restore;
import static io.fairspace.saturn.vocabulary.Vocabularies.initVocabularies;
import static org.apache.jena.tdb2.sys.DatabaseConnection.connectCreate;

@Slf4j
public class SaturnDatasetFactory {
    /**
     * Returns a dataset to work with.
     * We're playing Russian dolls here.
     * The original TDB2 dataset graph, which in fact consists of a number of wrappers itself (Jena uses wrappers everywhere),
     * is wrapped with a number of wrapper classes, each adding a new feature.
     * Currently it adds transaction logging, ElasticSearch indexing (if enabled) and applies default vocabulary if needed.
     */
    public static DatasetJobSupport connect(Config.Jena config) throws IOException {
        var elasticsearchClient = config.elasticSearch.enabled
                ? ElasticSearchClientFactory.build(config.elasticSearch.settings, config.elasticSearch.advancedSettings)
                : null;

        var restoreNeeded = isRestoreNeeded(config.datasetPath);

        // Create a TDB2 dataset graph
        var dsg = connectCreate(Location.create(config.datasetPath.getAbsolutePath()), config.storeParams).getDatasetGraph();

        var txnLog = new LocalTransactionLog(config.transactionLogPath, new SparqlTransactionCodec());

        if (elasticsearchClient != null) {
            // When a restore is needed, we instruct ES to delete the index first
            // This way, the index will be in sync with our current database
            dsg = enableElasticSearch(dsg, config, restoreNeeded, elasticsearchClient);
        }

        if (restoreNeeded) {
            restore(dsg, txnLog);
        }

        // Add transaction log
        dsg = new TxnLogDatasetGraph(dsg, txnLog);

        var dsgb = new DatasetGraphBatch(dsg);

        dsgb.calculateWrite(() -> {
            // Create a dataset
            var ds = DatasetFactory.wrap(dsgb);

            TypeMapper.getInstance().registerDatatype(MARKDOWN_DATA_TYPE);

            initVocabularies(ds);

            return null;
        });

        return new DatasetJobSupportImpl(dsgb);
    }

    protected static boolean isRestoreNeeded(File datasetPath) {
        return !datasetPath.exists() || datasetPath.list((dir, name) -> name.startsWith("Data-")).length == 0;
    }

    private static DatasetGraph enableElasticSearch(DatasetGraph dsg, Config.Jena config, boolean recreateIndex, Client client) throws UnknownHostException {
        try {
            // Setup ES client and index
            config.elasticSearch.settings.setIndexName("fairspace");
            ElasticSearchIndexConfigurer.configure(client, config.elasticSearch.settings, recreateIndex);

            // Create a dataset graph that updates ES with every triple update
            var textIndex = new TextIndexESBulk(new TextIndexConfig(new AutoEntityDefinition()), client, config.elasticSearch.settings.getIndexName());
            var textDocProducer = new SingleTripleTextDocProducer(textIndex, !config.elasticSearch.required);
            return TextDatasetFactory.create(dsg, textIndex, true, textDocProducer);
        } catch (Exception e) {
            log.error("Error connecting to ElasticSearch", e);
            if (config.elasticSearch.required) {
                throw e; // Terminates Saturn
            }
            return dsg;
        }
    }
}
