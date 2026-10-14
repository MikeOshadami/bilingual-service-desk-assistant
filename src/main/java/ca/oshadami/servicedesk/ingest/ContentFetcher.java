package ca.oshadami.servicedesk.ingest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Turns a web page, PDF or local file into clean plain text. For HTML it keeps only the main content and drops
 * navigation, headers and footers so that chunks contain policy text rather than site chrome.
 */
@Component
public class ContentFetcher {

    private static final String USER_AGENT = "BilingualServiceDeskAssistant/0.1 (portfolio demo; respects robots and rate limits)";
    private static final String BLOCKS = "h1, h2, h3, h4, h5, p, li, dt, dd, td, th, caption, summary";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public record FetchedContent(String text, String contentType) {
    }

    public FetchedContent fetch(SourceDefinition source) throws IOException, InterruptedException {
        if (source.isRemote()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(source.url()))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "fr".equals(source.lang()) ? "fr-CA,fr;q=0.9" : "en-CA,en;q=0.9")
                    .GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + " for " + source.url());
            }
            String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            return fromBytes(response.body(), type, source.url());
        }
        Path file = Path.of(source.path());
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String type = name.endsWith(".html") || name.endsWith(".htm") ? "text/html"
                : name.endsWith(".md") || name.endsWith(".txt") ? "text/plain" : "application/octet-stream";
        return fromBytes(Files.readAllBytes(file), type, file.toUri().toString());
    }

    public FetchedContent fromBytes(byte[] bytes, String contentType, String baseUri) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("html")) {
            return new FetchedContent(htmlToText(new String(bytes, StandardCharsets.UTF_8), baseUri), "text/html");
        }
        if (type.startsWith("text/plain") || type.contains("markdown")) {
            return new FetchedContent(new String(bytes, StandardCharsets.UTF_8).strip(), "text/plain");
        }
        return new FetchedContent(extractWithTika(bytes), type.isBlank() ? "application/octet-stream" : type);
    }

    /** PDF, Word, scanned text layers and other office formats via Apache Tika. */
    public String extractWithTika(byte[] bytes) {
        Resource resource = new ByteArrayResource(bytes);
        return new TikaDocumentReader(resource).get().stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"))
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    static String htmlToText(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        doc.select("script, style, noscript, nav, header, footer, form, iframe, svg, button, "
                + "[role=navigation], [role=banner], [role=contentinfo], .breadcrumb, .breadcrumbs, "
                + ".skip-link, .visually-hidden, .sr-only, [aria-hidden=true]").remove();
        Element root = doc.selectFirst("main");
        if (root == null) {
            root = doc.selectFirst("[role=main]");
        }
        if (root == null) {
            root = doc.body();
        }
        List<String> lines = new ArrayList<>();
        for (Element el : root.select(BLOCKS)) {
            // Only leaf blocks, so nested lists and paragraphs are not repeated.
            if (!el.select(BLOCKS).stream().filter(child -> child != el).findAny().isEmpty()) {
                continue;
            }
            String text = el.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            String tag = el.tagName();
            if (tag.matches("h[1-5]")) {
                lines.add("\n" + "#".repeat(Integer.parseInt(tag.substring(1))) + " " + text);
            } else if ("li".equals(tag) || "dd".equals(tag)) {
                lines.add("- " + text);
            } else {
                lines.add(text);
            }
        }
        String text = String.join("\n", lines).strip();
        if (text.length() < 200) {
            text = root.text().strip();
        }
        return text;
    }
}
