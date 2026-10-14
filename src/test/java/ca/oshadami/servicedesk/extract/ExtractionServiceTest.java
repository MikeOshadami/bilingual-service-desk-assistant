package ca.oshadami.servicedesk.extract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExtractionServiceTest {

    @Test
    void normalizesSloppyModelOutput() {
        ServiceRequestExtraction raw = new ServiceRequestExtraction(null, "French", " ", "null", null, null,
                " summary ", " ", null, null, 1.7);
        ServiceRequestExtraction n = ExtractionService.normalize(raw, "Bonjour, je veux renouveler mon permis");
        assertThat(n.requestType()).isEqualTo(RequestType.OTHER);
        assertThat(n.language()).isEqualTo("fr");
        assertThat(n.applicantName()).isNull();
        assertThat(n.email()).isNull();
        assertThat(n.referenceNumbers()).isEmpty();
        assertThat(n.summary()).isEqualTo("summary");
        assertThat(n.mainQuestion()).isNull();
        assertThat(n.urgency()).isEqualTo(ServiceRequestExtraction.Urgency.NORMAL);
        assertThat(n.confidence()).isEqualTo(1.0);
    }

    @Test
    void everyRequestTypeHasAQueue() {
        for (RequestType type : RequestType.values()) {
            assertThat(type.queue()).isNotBlank();
        }
    }
}
