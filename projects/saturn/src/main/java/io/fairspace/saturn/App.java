package io.fairspace.saturn;

import com.google.common.eventbus.EventBus;
import io.fairspace.saturn.auth.DummyAuthenticator;
import io.fairspace.saturn.events.*;
import io.fairspace.saturn.rdf.SaturnDatasetFactory;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.services.collections.CollectionsApp;
import io.fairspace.saturn.services.collections.CollectionsService;
import io.fairspace.saturn.services.health.HealthApp;
import io.fairspace.saturn.services.mail.MailService;
import io.fairspace.saturn.services.metadata.*;
import io.fairspace.saturn.services.metadata.validation.*;
import io.fairspace.saturn.services.permissions.PermissionNotificationHandler;
import io.fairspace.saturn.services.permissions.PermissionsApp;
import io.fairspace.saturn.services.permissions.PermissionsServiceImpl;
import io.fairspace.saturn.services.users.UserApp;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vfs.CompoundFileSystem;
import io.fairspace.saturn.vfs.irods.IRODSVirtualFileSystem;
import io.fairspace.saturn.vfs.managed.LocalBlobStore;
import io.fairspace.saturn.vfs.managed.ManagedFileSystem;
import io.fairspace.saturn.webdav.MiltonWebDAVServlet;
import io.fairspace.saturn.webdav.WebdavEventEmitter;
import io.milton.http.Request;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfconnection.Isolation;
import org.apache.jena.rdfconnection.RDFConnectionLocal;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.fairspace.saturn.ConfigLoader.CONFIG;
import static io.fairspace.saturn.SaturnSecurityHandler.userInfo;
import static io.fairspace.saturn.auth.SecurityUtil.createAuthenticator;
import static io.fairspace.saturn.vocabulary.Vocabularies.*;
import static org.apache.jena.sparql.core.Quad.defaultGraphIRI;

@Slf4j
public class App {
    private static final String API_VERSION = "v1";

    public static void main(String[] args) throws Exception {
        log.info("Saturn is starting");

        var ds = SaturnDatasetFactory.connect(CONFIG.jena, SaturnSecurityHandler::userInfo);

        // The RDF connection is supposed to be thread-safe and can
        // be reused in all the application
        var rdf = new RDFConnectionLocal(ds, Isolation.COPY);
        initVocabularies(rdf);

        var eventBus = new EventBus();

        var userService = new UserService(CONFIG.auth.userUrlTemplate, new DAO(rdf, null));
        Supplier<Node> userIriSupplier = () -> userService.getUserIri(userInfo().getSubjectClaim());

        EventService eventService = setupEventService();

        var mailService = new MailService(CONFIG.mail);
        var permissionNotificationHandler = new PermissionNotificationHandler(rdf, userService, mailService, CONFIG.publicUrl);
        var permissions = new PermissionsServiceImpl(rdf, userIriSupplier, permissionNotificationHandler, eventService);

        var collections = new CollectionsService(new DAO(rdf, userIriSupplier), eventBus::post, permissions, eventService);
        var blobStore = new LocalBlobStore(new File(CONFIG.webDAV.blobStorePath));
        var fs = new CompoundFileSystem(collections, Map.of(
                ManagedFileSystem.TYPE, new ManagedFileSystem(rdf, blobStore, userIriSupplier, collections, eventBus),
                IRODSVirtualFileSystem.TYPE, new IRODSVirtualFileSystem(rdf, collections)));

        var metadataLifeCycleManager = new MetadataEntityLifeCycleManager(rdf, defaultGraphIRI, VOCABULARY_GRAPH_URI, userIriSupplier, permissions);

        var metadataValidator = new ComposedValidator(
                new MachineOnlyClassesValidator(),
                new ProtectMachineOnlyPredicatesValidator(),
                new PermissionCheckingValidator(permissions),
                new ShaclValidator());

        Consumer<MetadataEvent.Type> metadataEventConsumer = type ->
            eventService.emitEvent(MetadataEvent.builder()
                    .category(EventCategory.METADATA)
                    .eventType(type)
                    .build()
            );

        var metadataService = new ChangeableMetadataService(rdf, defaultGraphIRI, VOCABULARY_GRAPH_URI, CONFIG.jena.maxTriplesToReturn, metadataLifeCycleManager, metadataValidator, metadataEventConsumer);

        var vocabularyValidator = new ComposedValidator(
                new ProtectMachineOnlyPredicatesValidator(),
                new ShaclValidator(),
                new SystemVocabularyProtectingValidator(),
                new MetadataAndVocabularyConsistencyValidator(rdf),
                new InverseForUsedPropertiesValidator(rdf)
        );
        var vocabularyLifeCycleManager = new MetadataEntityLifeCycleManager(rdf, VOCABULARY_GRAPH_URI, META_VOCABULARY_GRAPH_URI, userIriSupplier);

        Consumer<MetadataEvent.Type> vocabularyEventConsumer = type ->
                eventService.emitEvent(MetadataEvent.builder()
                        .category(EventCategory.VOCABULARY)
                        .eventType(type)
                        .build()
                );

        var userVocabularyService = new ChangeableMetadataService(rdf, VOCABULARY_GRAPH_URI, META_VOCABULARY_GRAPH_URI, vocabularyLifeCycleManager, vocabularyValidator, vocabularyEventConsumer);
        var metaVocabularyService = new ReadableMetadataService(rdf, META_VOCABULARY_GRAPH_URI, META_VOCABULARY_GRAPH_URI);

        var auth = CONFIG.auth;
        if (!auth.enabled) {
            log.warn("Authentication is disabled");
        }
        var authenticator = auth.enabled
                ? createAuthenticator(auth.jwksUrl, auth.jwtAlgorithm)
                : new DummyAuthenticator(CONFIG.auth.developerRoles);
        var apiPathPrefix = "/api/" + API_VERSION;
        var securityHandler = new SaturnSecurityHandler(apiPathPrefix, CONFIG.auth, authenticator, userInfo -> userService.onAuthorized(userInfo));

        FusekiServer.create()
                .securityHandler(securityHandler)
                .add(apiPathPrefix + "/rdf/", ds, false)
                .addFilter(apiPathPrefix + "/*", new SaturnSparkFilter(apiPathPrefix,
                        new ChangeableMetadataApp("/metadata", metadataService, CONFIG.jena.metadataBaseIRI),
                        new ChangeableMetadataApp("/vocabulary", userVocabularyService, CONFIG.jena.vocabularyBaseIRI),
                        new ReadableMetadataApp("/meta-vocabulary", metaVocabularyService),
                        new CollectionsApp("/collections", collections),
                        new PermissionsApp("/permissions", permissions),
                        new UserApp("/users", userService),
                        new HealthApp("/health")))
                .addServlet("/webdav/" + API_VERSION + "/*", new MiltonWebDAVServlet(
                        "/webdav/" + API_VERSION + "/",
                        fs,
                        new WebdavEventEmitter(eventService)
                ))
                .port(CONFIG.port)
                .build()
                .start();

        log.info("Saturn has started");
    }

    private static EventService setupEventService() throws Exception {
        if(CONFIG.rabbitMQ.enabled) {
            try {
                var eventService = new RabbitMQEventService(CONFIG.rabbitMQ, CONFIG.workspace.name, SaturnSecurityHandler::userInfo);
                eventService.init();
                return eventService;
            } catch(Exception e) {
                log.error("Error connecting to RabbitMQ", e);

                if(CONFIG.rabbitMQ.required) {
                   throw e;
                }

                log.warn("Continuing without event functionality");
            }
        } else {
            log.warn("Logging to rabbitMQ is disabled due to configuration settings. Set rabbitMQ.enabled to true to enable logging");
        }

        return event -> log.trace("Logging events is disabled in configuration. Set rabbitMQ.enabled to true");
    }
}