package ca.oshadami.servicedesk.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import ca.oshadami.servicedesk.config.AppProperties;
import ca.oshadami.servicedesk.config.Audience;

/**
 * Loads and validates the list of knowledge sources from a YAML file.
 */
@Component
public class SourceCatalog {

    record SourceFile(List<SourceDefinition> sources) {
    }

    private final AppProperties props;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public SourceCatalog(AppProperties props) {
        this.props = props;
    }

    public List<SourceDefinition> load() {
        Path file = Path.of(props.ingest().sourcesFile());
        try (InputStream in = Files.newInputStream(file)) {
            List<SourceDefinition> sources = yaml.readValue(in, SourceFile.class).sources();
            validate(sources);
            return sources;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read sources file " + file.toAbsolutePath(), e);
        }
    }

    static void validate(List<SourceDefinition> sources) {
        Set<String> ids = new HashSet<>();
        for (SourceDefinition s : sources) {
            if (s.id() == null || s.id().isBlank() || !s.id().matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException("Source id must be lowercase letters, digits and dashes: " + s.id());
            }
            if (!ids.add(s.id())) {
                throw new IllegalArgumentException("Duplicate source id: " + s.id());
            }
            boolean hasUrl = s.url() != null && !s.url().isBlank();
            boolean hasPath = s.path() != null && !s.path().isBlank();
            if (hasUrl == hasPath) {
                throw new IllegalArgumentException("Source " + s.id() + " needs exactly one of url or path");
            }
            if (!"en".equals(s.lang()) && !"fr".equals(s.lang())) {
                throw new IllegalArgumentException("Source " + s.id() + " lang must be en or fr");
            }
            Audience.from(s.audience());
        }
    }
}
