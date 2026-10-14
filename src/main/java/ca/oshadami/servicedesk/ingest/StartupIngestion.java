package ca.oshadami.servicedesk.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads the knowledge base on first start, so a fresh `docker compose up` gives a working demo.
 */
@Component
@ConditionalOnProperty(name = "app.ingest.on-startup", havingValue = "true")
public class StartupIngestion implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupIngestion.class);

    private final IngestionService ingestion;

    public StartupIngestion(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = ingestion.chunkCount();
        if (existing > 0) {
            log.info("Knowledge base already has {} chunks; skipping startup ingestion", existing);
            return;
        }
        log.info("Knowledge base is empty; ingesting sources");
        ingestion.ingestAll().forEach(r -> log.info("  {} -> {} ({} chunks){}", r.id(), r.status(), r.chunks(),
                r.error() == null ? "" : " " + r.error()));
    }
}
