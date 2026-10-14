package ca.oshadami.servicedesk.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageDetectorTest {

    @Test
    void detectsFrench() {
        assertThat(LanguageDetector.detect("Comment renouveler mon permis de conduire?")).isEqualTo("fr");
        assertThat(LanguageDetector.detect("Quels documents dois-je apporter pour l'immatriculation?")).isEqualTo("fr");
    }

    @Test
    void detectsEnglish() {
        assertThat(LanguageDetector.detect("How do I renew my driver's licence?")).isEqualTo("en");
    }

    @Test
    void explicitLanguageWins() {
        assertThat(LanguageDetector.normalize("fr-CA", "How do I renew?")).isEqualTo("fr");
        assertThat(LanguageDetector.normalize(null, "")).isEqualTo("en");
    }
}
