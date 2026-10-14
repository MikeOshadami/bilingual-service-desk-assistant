package ca.oshadami.servicedesk.ingest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ca.oshadami.servicedesk.config.AppProperties;

class SourceCatalogTest {

    @Test
    void shippedSourcesFileIsValid() {
        AppProperties props = new AppProperties(null, new AppProperties.Ingest(Path.of("data/sources.yaml").toString(), false, 400),
                null, null, null);
        assertThatCode(() -> new SourceCatalog(props).load()).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateIds() {
        SourceDefinition a = new SourceDefinition("a", "A", "https://x", null, "en", "public", null);
        assertThatThrownBy(() -> SourceCatalog.validate(List.of(a, a))).hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsSourceWithBothUrlAndPath() {
        SourceDefinition s = new SourceDefinition("a", "A", "https://x", "file.md", "en", "public", null);
        assertThatThrownBy(() -> SourceCatalog.validate(List.of(s))).hasMessageContaining("exactly one");
    }

    @Test
    void rejectsUnknownAudience() {
        SourceDefinition s = new SourceDefinition("a", "A", "https://x", null, "en", "everyone", null);
        assertThatThrownBy(() -> SourceCatalog.validate(List.of(s))).hasMessageContaining("Unknown audience");
    }
}
