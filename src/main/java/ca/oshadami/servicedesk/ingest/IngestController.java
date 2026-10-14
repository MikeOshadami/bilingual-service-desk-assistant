package ca.oshadami.servicedesk.ingest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ca.oshadami.servicedesk.audit.AuditService;
import ca.oshadami.servicedesk.config.CallerContext;

/**
 * Staff-only endpoints to (re)load the knowledge base.
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestionService ingestion;
    private final ContentFetcher fetcher;
    private final AuditService audit;
    private final CallerContext caller;

    public IngestController(IngestionService ingestion, ContentFetcher fetcher, AuditService audit, CallerContext caller) {
        this.ingestion = ingestion;
        this.fetcher = fetcher;
        this.audit = audit;
        this.caller = caller;
    }

    /** Re-ingests every source in sources.yaml. */
    @PostMapping("/sources")
    public List<IngestionService.SourceResult> ingestSources() {
        List<IngestionService.SourceResult> results = ingestion.ingestAll();
        long failed = results.stream().filter(r -> "failed".equals(r.status())).count();
        audit.record(caller.actor(), "ingest.sources", failed == 0 ? "success" : "partial",
                results.size() + " sources, " + failed + " failed");
        return results;
    }

    /** Uploads a single document (PDF, Word, HTML, text) into the knowledge base. */
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionService.SourceResult ingestFile(@RequestParam("file") MultipartFile file,
            @RequestParam String id, @RequestParam String title,
            @RequestParam(defaultValue = "en") String lang,
            @RequestParam(defaultValue = "public") String audience,
            @RequestParam(defaultValue = "general") String topic) throws IOException {
        SourceDefinition source = new SourceDefinition(id, title, null, "upload:" + file.getOriginalFilename(), lang, audience, topic);
        SourceCatalog.validate(List.of(source));
        String text = fetcher.fromBytes(file.getBytes(), file.getContentType(), source.path()).text();
        int chunks = ingestion.store(source, text);
        audit.record(caller.actor(), "ingest.file", "success", "id=" + id + " audience=" + audience + " chunks=" + chunks);
        return new IngestionService.SourceResult(id, "ingested", chunks, text.length(), null);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("chunks", ingestion.chunkCount());
    }
}
