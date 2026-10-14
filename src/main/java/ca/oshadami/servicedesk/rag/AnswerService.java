package ca.oshadami.servicedesk.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import ca.oshadami.servicedesk.audit.AuditService;
import ca.oshadami.servicedesk.common.LanguageDetector;
import ca.oshadami.servicedesk.config.AppProperties;
import ca.oshadami.servicedesk.config.Audience;
import ca.oshadami.servicedesk.config.CallerContext;
import ca.oshadami.servicedesk.guard.PromptInjectionGuard;
import ca.oshadami.servicedesk.privacy.PiiRedactor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Retrieval-augmented answering with citations.
 * <ol>
 * <li>Screen the question for prompt injection.</li>
 * <li>Retrieve the closest chunks the caller is allowed to see (audience filter applied in the vector query).</li>
 * <li>If nothing clears the similarity threshold, answer "not found" without calling the model.</li>
 * <li>Ask the local model to answer only from the numbered sources and cite them.</li>
 * <li>Validate citations, record metrics and a redacted audit event.</li>
 * </ol>
 */
@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptInjectionGuard guard;
    private final PiiRedactor redactor;
    private final AuditService audit;
    private final CallerContext caller;
    private final AppProperties props;
    private final MeterRegistry meters;

    public AnswerService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, PromptInjectionGuard guard,
            PiiRedactor redactor, AuditService audit, CallerContext caller, AppProperties props, MeterRegistry meters) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.guard = guard;
        this.redactor = redactor;
        this.audit = audit;
        this.caller = caller;
        this.props = props;
        this.meters = meters;
    }

    public AskResponse ask(AskRequest request) {
        return ask(request, caller.allowedAudiences(), caller.actor());
    }

    public AskResponse ask(AskRequest request, Set<Audience> audiences, String actor) {
        Timer.Sample sample = Timer.start(meters);
        long start = System.nanoTime();
        String lang = LanguageDetector.normalize(request.lang(), request.question());

        PromptInjectionGuard.Verdict verdict = guard.inspect(request.question());
        if (verdict.blocked()) {
            meters.counter("servicedesk.guard.blocked", "endpoint", "ask").increment();
            audit.record(actor, "ask", "blocked", "reasons=" + verdict.reasons() + " q=" + request.question());
            return finish(sample, start, new AskResponse(PromptTemplates.blockedMessage(lang), lang, false,
                    AskResponse.Status.BLOCKED, List.of(), 0));
        }

        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(request.question())
                .topK(props.rag().topK())
                .similarityThreshold(props.rag().similarityThreshold())
                .filterExpression(CallerContext.audienceFilter(audiences))
                .build());

        if (docs.isEmpty()) {
            audit.record(actor, "ask", "no_answer", "no sources above threshold; q=" + request.question());
            return finish(sample, start, new AskResponse(PromptTemplates.noAnswerMessage(lang), lang, false,
                    AskResponse.Status.NO_ANSWER, List.of(), 0));
        }

        String raw = chatClient.prompt()
                .system(PromptTemplates.answerSystemPrompt(LanguageDetector.displayName(lang), props.rag().maxAnswerWords()))
                .user(PromptTemplates.answerUserPrompt(request.question(), docs))
                .options(OllamaChatOptions.builder().temperature(0.1).build())
                .call()
                .content();
        String answer = raw == null ? "" : raw.strip();

        // Small models sometimes append the token after a partial answer; only a leading token means "declined".
        if (answer.isEmpty() || answer.startsWith(PromptTemplates.NO_ANSWER_TOKEN)) {
            audit.record(actor, "ask", "no_answer", "model declined; q=" + request.question());
            return finish(sample, start, new AskResponse(PromptTemplates.noAnswerMessage(lang), lang, false,
                    AskResponse.Status.NO_ANSWER, toSources(docs, Set.of()), 0));
        }

        answer = CitationParser.stripInvalid(answer.replace(PromptTemplates.NO_ANSWER_TOKEN, "").strip(), docs.size());
        Set<Integer> cited = CitationParser.cited(answer, docs.size());
        boolean grounded = !cited.isEmpty();
        meters.counter("servicedesk.answers", "grounded", String.valueOf(grounded), "lang", lang).increment();
        audit.record(actor, "ask", grounded ? "answered" : "answered_uncited",
                "lang=" + lang + " cited=" + cited + " q=" + request.question());
        log.info("ask lang={} sources={} cited={} q=\"{}\"", lang, docs.size(), cited, redactor.forLog(request.question(), 120));

        return finish(sample, start, new AskResponse(answer, lang, grounded, AskResponse.Status.ANSWERED,
                toSources(docs, cited), 0));
    }

    private AskResponse finish(Timer.Sample sample, long start, AskResponse r) {
        sample.stop(meters.timer("servicedesk.ask.latency", "status", r.status().name()));
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new AskResponse(r.answer(), r.lang(), r.grounded(), r.status(), r.sources(), ms);
    }

    private static List<AskResponse.Source> toSources(List<Document> docs, Set<Integer> cited) {
        List<AskResponse.Source> sources = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String text = d.getText() == null ? "" : d.getText().replaceAll("\\s+", " ").strip();
            sources.add(new AskResponse.Source(i + 1,
                    str(d, "source_id"), str(d, "title"), str(d, "url"), str(d, "lang"), str(d, "audience"),
                    d.getScore() == null ? 0 : Math.round(d.getScore() * 1000) / 1000.0,
                    cited.contains(i + 1),
                    text.length() > 240 ? text.substring(0, 240) + "..." : text));
        }
        return sources;
    }

    private static String str(Document d, String key) {
        Object v = d.getMetadata().get(key);
        return v == null ? null : v.toString();
    }
}
