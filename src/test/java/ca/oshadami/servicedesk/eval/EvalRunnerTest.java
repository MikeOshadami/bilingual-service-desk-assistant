package ca.oshadami.servicedesk.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ca.oshadami.servicedesk.rag.AskResponse;

class EvalRunnerTest {

    private static AskResponse answered(String text, String citedSource, String otherSource) {
        return new AskResponse(text, "en", true, AskResponse.Status.ANSWERED, List.of(
                new AskResponse.Source(1, citedSource, "t", "u", "en", "public", 0.8, true, "x"),
                new AskResponse.Source(2, otherSource, "t", "u", "en", "public", 0.6, false, "x")), 100);
    }

    @Test
    void passesWhenExpectedSourceCitedAndKeywordsPresent() {
        var c = new EvalRunner.EvalCase("q1", "?", "en", "public", List.of("id-documents-en"), List.of("passport"), null, false, null);
        var o = EvalRunner.score(c, answered("Bring a passport [1].", "id-documents-en", "photo-id-en"));
        assertThat(o.pass()).isTrue();
    }

    @Test
    void failsWhenStaffSourceShownToPublic() {
        var c = new EvalRunner.EvalCase("q2", "?", "en", "public", null, null, null, false, List.of("escalation-procedure-staff"));
        var o = EvalRunner.score(c, answered("Something [1].", "driver-licences-en", "escalation-procedure-staff"));
        assertThat(o.leakFree()).isFalse();
        assertThat(o.pass()).isFalse();
    }

    @Test
    void citedButWrongFactFails() {
        var c = new EvalRunner.EvalCase("q4", "?", "en", "public", List.of("new-residents-en"), List.of("as soon as"),
                List.of("six months"), false, null);
        var o = EvalRunner.score(c, answered("You must exchange it as soon as you move, or within six months [1].",
                "new-residents-en", "x"));
        assertThat(o.sourceOk()).isTrue();
        assertThat(o.noForbidden()).isFalse();
        assertThat(o.pass()).isFalse();
    }

    @Test
    void keywordAlternativesAreAccepted() {
        assertThat(EvalRunner.containsAny("there is no minimum age [1].", "no age|no minimum age")).isTrue();
        assertThat(EvalRunner.containsAny("you must be 16 [1].", "no age|no minimum age")).isFalse();
    }

    @Test
    void outOfScopeQuestionMustBeDeclined() {
        var c = new EvalRunner.EvalCase("q3", "?", "en", "public", null, null, null, true, null);
        var o = EvalRunner.score(c, answered("Here is a cake recipe [1].", "a", "b"));
        assertThat(o.noAnswerOk()).isFalse();
    }
}
