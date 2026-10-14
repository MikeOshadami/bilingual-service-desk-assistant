package ca.oshadami.servicedesk.ingest;

/**
 * One knowledge source from sources.yaml. Exactly one of {@code url} or {@code path} is set.
 *
 * @param id       stable identifier, used for citations, re-ingestion and evaluation
 * @param title    human-readable title shown in citations
 * @param url      public web page or PDF
 * @param path     local file (relative to the working directory), e.g. an internal procedure
 * @param lang     "en" or "fr"
 * @param audience "public" or "staff"
 * @param topic    free-form grouping, e.g. "driver-licences"
 */
public record SourceDefinition(String id, String title, String url, String path, String lang, String audience, String topic) {

    public String location() {
        return url != null && !url.isBlank() ? url : path;
    }

    public boolean isRemote() {
        return url != null && !url.isBlank();
    }
}
