package ca.oshadami.servicedesk.workflow;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ca.oshadami.servicedesk.extract.ExtractionService;
import ca.oshadami.servicedesk.extract.ServiceRequestExtraction;
import ca.oshadami.servicedesk.ingest.ContentFetcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Intake endpoints. JSON is used by the web UI and by n8n; multipart accepts a document (PDF, Word, scanned text).
 */
@RestController
@RequestMapping("/api")
public class RequestController {

    public record IntakeRequest(@NotBlank @Size(max = 20000) String text, String channel) {
    }

    private final TriageService triage;
    private final ExtractionService extraction;
    private final ContentFetcher fetcher;

    public RequestController(TriageService triage, ExtractionService extraction, ContentFetcher fetcher) {
        this.triage = triage;
        this.extraction = extraction;
        this.fetcher = fetcher;
    }

    /** Full workflow: extract, route, draft a reply and create a case. */
    @PostMapping(value = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TriageResult submit(@Valid @RequestBody IntakeRequest request) {
        return triage.triage(request.text(), request.channel());
    }

    @PostMapping(value = "/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TriageResult submitDocument(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "upload") String channel) throws IOException {
        return triage.triage(documentText(file), channel);
    }

    /** Extraction only, no case created. Useful for testing the document processing step on its own. */
    @PostMapping(value = "/extract", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ServiceRequestExtraction extract(@Valid @RequestBody IntakeRequest request) {
        return extraction.extract(request.text());
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ServiceRequestExtraction extractDocument(@RequestParam("file") MultipartFile file) throws IOException {
        return extraction.extract(documentText(file));
    }

    private String documentText(MultipartFile file) throws IOException {
        String text = fetcher.fromBytes(file.getBytes(), file.getContentType(), "upload").text();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No readable text found in the uploaded file. Scanned images need OCR first.");
        }
        return text;
    }
}
