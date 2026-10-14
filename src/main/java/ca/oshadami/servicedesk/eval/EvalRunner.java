package ca.oshadami.servicedesk.eval;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import ca.oshadami.servicedesk.config.Audience;
import ca.oshadami.servicedesk.rag.AnswerService;
import ca.oshadami.servicedesk.rag.AskRequest;
import ca.oshadami.servicedesk.rag.AskResponse;

/**
 * Offline evaluation of the RAG pipeline against a fixed question set. Run after changing prompts, models, chunk
 * size or thresholds, and compare reports:
 *
 * <pre>./mvnw spring-boot:run -Dspring-boot.run.profiles=eval</pre>
 *
 * Checks per question: the expected source was cited (retrieval and grounding), expected facts appear in the answer,
 * known wrong facts do not (a citation alone does not prove the answer is right), out-of-scope questions are declined,
 * and staff-only content never reaches a public caller.
 */
@Component
@Profile("eval")
public class EvalRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    public record EvalCase(String id, String question, String lang, String audience, List<String> expectSources,
            List<String> expectKeywords, List<String> forbidKeywords, boolean expectNoAnswer, List<String> forbidSources) {
    }

    record EvalFile(List<EvalCase> cases) {
    }

    record Outcome(EvalCase c, AskResponse r, boolean sourceOk, double keywordScore, boolean noForbidden,
            boolean noAnswerOk, boolean leakFree, boolean langOk) {
        boolean pass() {
            return sourceOk && keywordScore >= 0.5 && noForbidden && noAnswerOk && leakFree && langOk;
        }
    }

    private final AnswerService answers;
    private final ApplicationContext context;
    private final String questionsFile;
    private final String reportFile;

    public EvalRunner(AnswerService answers, ApplicationContext context,
            @Value("${app.eval.questions-file:eval/questions.yaml}") String questionsFile,
            @Value("${app.eval.report-file:eval/report.md}") String reportFile) {
        this.answers = answers;
        this.context = context;
        this.questionsFile = questionsFile;
        this.reportFile = reportFile;
    }

    @Override
    public void run(String... args) throws Exception {
        List<EvalCase> cases = load();
        List<Outcome> outcomes = new ArrayList<>();
        for (EvalCase c : cases) {
            Set<Audience> audiences = Audience.from(c.audience()) == Audience.STAFF
                    ? Set.of(Audience.PUBLIC, Audience.STAFF) : Set.of(Audience.PUBLIC);
            AskResponse r = answers.ask(new AskRequest(c.question(), c.lang()), audiences, "eval");
            outcomes.add(score(c, r));
            log.info("eval {} -> {} ({} ms)", c.id(), outcomes.getLast().pass() ? "PASS" : "FAIL", r.latencyMs());
        }
        String report = report(outcomes);
        Path out = Path.of(reportFile);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, report);
        long passed = outcomes.stream().filter(Outcome::pass).count();
        log.info("Evaluation finished: {}/{} passed. Report written to {}", passed, outcomes.size(), out.toAbsolutePath());
        System.exit(SpringApplication.exit(context, () -> passed == outcomes.size() ? 0 : 1));
    }

    private List<EvalCase> load() throws IOException {
        try (InputStream in = Files.newInputStream(Path.of(questionsFile))) {
            return new ObjectMapper(new YAMLFactory()).readValue(in, EvalFile.class).cases();
        }
    }

    static Outcome score(EvalCase c, AskResponse r) {
        List<String> citedIds = r.sources().stream().filter(AskResponse.Source::cited).map(AskResponse.Source::sourceId).toList();
        List<String> shownIds = r.sources().stream().map(AskResponse.Source::sourceId).toList();

        boolean noAnswerOk = c.expectNoAnswer() ? r.status() != AskResponse.Status.ANSWERED : r.status() == AskResponse.Status.ANSWERED;
        boolean sourceOk = c.expectSources() == null || c.expectSources().isEmpty()
                || c.expectSources().stream().anyMatch(citedIds::contains);
        double keywordScore = 1.0;
        if (c.expectKeywords() != null && !c.expectKeywords().isEmpty()) {
            String answer = r.answer().toLowerCase(Locale.ROOT);
            long hits = c.expectKeywords().stream().filter(k -> containsAny(answer, k)).count();
            keywordScore = (double) hits / c.expectKeywords().size();
        }
        String lowerAnswer = r.answer().toLowerCase(Locale.ROOT);
        boolean noForbidden = c.forbidKeywords() == null
                || c.forbidKeywords().stream().noneMatch(k -> containsAny(lowerAnswer, k));
        boolean leakFree = c.forbidSources() == null || c.forbidSources().stream().noneMatch(shownIds::contains);
        boolean langOk = c.lang() == null || c.lang().equals(r.lang());
        return new Outcome(c, r, sourceOk, keywordScore, noForbidden, noAnswerOk, leakFree, langOk);
    }

    /** A keyword may list accepted alternatives separated by "|", e.g. "no age|no minimum age". */
    static boolean containsAny(String lowerText, String keyword) {
        for (String alt : keyword.split("\\|")) {
            if (!alt.isBlank() && lowerText.contains(alt.strip().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static String report(List<Outcome> outcomes) {
        long passed = outcomes.stream().filter(Outcome::pass).count();
        double avgLatency = outcomes.stream().mapToLong(o -> o.r().latencyMs()).average().orElse(0);
        long grounded = outcomes.stream().filter(o -> o.r().grounded()).count();
        long answeredExpected = outcomes.stream().filter(o -> !o.c().expectNoAnswer()).count();

        StringBuilder sb = new StringBuilder();
        sb.append("# Evaluation report\n\n");
        sb.append("Generated ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        sb.append("| Metric | Value |\n|---|---|\n");
        sb.append("| Cases passed | ").append(passed).append(" / ").append(outcomes.size()).append(" |\n");
        sb.append("| Grounded answers (cited a source) | ").append(grounded).append(" / ").append(answeredExpected).append(" answerable |\n");
        sb.append("| Average latency | ").append(Math.round(avgLatency)).append(" ms |\n\n");
        sb.append("| Case | Result | Status | Expected source cited | Keywords | No wrong facts | Declined when expected | No staff leak | Language |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Outcome o : outcomes) {
            sb.append("| ").append(o.c().id())
                    .append(" | ").append(o.pass() ? "PASS" : "FAIL")
                    .append(" | ").append(o.r().status())
                    .append(" | ").append(mark(o.sourceOk()))
                    .append(" | ").append(Math.round(o.keywordScore() * 100)).append("%")
                    .append(" | ").append(mark(o.noForbidden()))
                    .append(" | ").append(mark(o.noAnswerOk()))
                    .append(" | ").append(mark(o.leakFree()))
                    .append(" | ").append(mark(o.langOk()))
                    .append(" |\n");
        }
        sb.append("\n## Answers\n");
        for (Outcome o : outcomes) {
            sb.append("\n### ").append(o.c().id()).append("\n\n**Q:** ").append(o.c().question()).append("\n\n**A:** ")
                    .append(o.r().answer().replace("\n", "\n> ")).append("\n");
        }
        return sb.toString();
    }

    private static String mark(boolean ok) {
        return ok ? "yes" : "**no**";
    }
}
