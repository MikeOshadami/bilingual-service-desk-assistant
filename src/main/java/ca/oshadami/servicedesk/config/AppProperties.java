package ca.oshadami.servicedesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under the {@code app.*} prefix. See application.yml for defaults.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Rag rag, Ingest ingest, Security security, CaseApi caseApi, Extraction extraction) {

    /** Retrieval settings. */
    public record Rag(int topK, double similarityThreshold, int maxAnswerWords) {
    }

    /** Where knowledge sources come from and when they are loaded. */
    public record Ingest(String sourcesFile, boolean onStartup, int chunkSizeTokens) {
    }

    /** When disabled, every caller is treated as an anonymous member of the public. */
    public record Security(boolean enabled, String expectedIssuer) {
    }

    /** The downstream case management system that triaged requests are written to. */
    public record CaseApi(String baseUrl) {
    }

    /** Document extraction limits and review thresholds. */
    public record Extraction(int maxInputChars, double reviewConfidenceThreshold) {
    }
}
