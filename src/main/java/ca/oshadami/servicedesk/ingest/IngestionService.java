package ca.oshadami.servicedesk.ingest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ca.oshadami.servicedesk.config.AppProperties;
import ca.oshadami.servicedesk.config.Audience;

/**
 * Fetches sources, splits them into chunks, embeds them with the local embedding model and stores them in pgvector.
 * Re-ingesting a source replaces its previous chunks, so the run is idempotent.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    public record SourceResult(String id, String status, int chunks, int characters, String error) {
    }

    private final SourceCatalog catalog;
    private final ContentFetcher fetcher;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final TokenTextSplitter splitter;

    public IngestionService(SourceCatalog catalog, ContentFetcher fetcher, VectorStore vectorStore, JdbcTemplate jdbc,
            AppProperties props) {
        this.catalog = catalog;
        this.fetcher = fetcher;
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(props.ingest().chunkSizeTokens())
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(20)
                .withKeepSeparator(true)
                .build();
    }

    public List<SourceResult> ingestAll() {
        List<SourceResult> results = new ArrayList<>();
        for (SourceDefinition source : catalog.load()) {
            results.add(ingest(source));
        }
        return results;
    }

    public SourceResult ingest(SourceDefinition source) {
        try {
            ContentFetcher.FetchedContent content = fetcher.fetch(source);
            if (content.text().isBlank()) {
                return new SourceResult(source.id(), "skipped", 0, 0, "no text extracted");
            }
            int chunks = store(source, content.text());
            log.info("Ingested source={} chunks={} chars={}", source.id(), chunks, content.text().length());
            return new SourceResult(source.id(), "ingested", chunks, content.text().length(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SourceResult(source.id(), "failed", 0, 0, "interrupted");
        } catch (Exception e) {
            log.warn("Failed to ingest source={}: {}", source.id(), e.getMessage());
            return new SourceResult(source.id(), "failed", 0, 0, e.getMessage());
        }
    }

    /** Stores already-extracted text, replacing any previous chunks for the same source id. */
    public int store(SourceDefinition source, String text) {
        Audience audience = Audience.from(source.audience());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_id", source.id());
        metadata.put("title", source.title());
        metadata.put("url", source.location());
        metadata.put("lang", source.lang());
        metadata.put("audience", audience.value());
        metadata.put("topic", source.topic() == null ? "general" : source.topic());
        metadata.put("ingested_at", Instant.now().toString());

        List<Document> chunks = splitter.apply(List.of(new Document(text, metadata)));
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("chunk", i);
        }
        vectorStore.delete("source_id == '" + source.id() + "'");
        vectorStore.add(chunks);
        return chunks.size();
    }

    public long chunkCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM vector_store", Long.class);
        return count == null ? 0 : count;
    }
}
