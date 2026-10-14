package ca.oshadami.servicedesk.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitationParserTest {

    @Test
    void findsValidCitationsOnly() {
        assertThat(CitationParser.cited("Bring ID [1]. Pay the fee [2][3]. See [9].", 3)).containsExactly(1, 2, 3);
    }

    @Test
    void stripsCitationsToSourcesThatWereNotRetrieved() {
        assertThat(CitationParser.stripInvalid("Fact [1]. Invented [7].", 2)).isEqualTo("Fact [1]. Invented .");
    }
}
