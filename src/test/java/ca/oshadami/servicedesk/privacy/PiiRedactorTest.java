package ca.oshadami.servicedesk.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void masksCanadianIdentifiers() {
        String input = "Jordan, jordan.leblanc@example.com, 506-555-0142, SIN 123 456 789, E1A 2B3, licence 1234567";
        String out = redactor.redact(input);
        assertThat(out)
                .contains("[EMAIL]", "[PHONE]", "[SIN]", "[POSTAL_CODE]", "[ID_NUMBER]")
                .doesNotContain("jordan.leblanc", "555-0142", "456", "E1A", "1234567");
    }

    @Test
    void phoneIsNotMistakenForSin() {
        assertThat(redactor.redact("call (506) 555-0142 please")).isEqualTo("call [PHONE] please");
    }

    @Test
    void leavesOrdinaryTextAlone() {
        String text = "How do I renew a Class 5 licence before the 30th?";
        assertThat(redactor.redact(text)).isEqualTo(text);
    }

    @Test
    void forLogTruncatesAndFlattens() {
        assertThat(redactor.forLog("line one\n\nline two", 10)).isEqualTo("line one l...");
    }
}
