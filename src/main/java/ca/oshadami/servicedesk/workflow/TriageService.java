package ca.oshadami.servicedesk.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import ca.oshadami.servicedesk.audit.AuditService;
import ca.oshadami.servicedesk.config.AppProperties;
import ca.oshadami.servicedesk.config.Audience;
import ca.oshadami.servicedesk.config.CallerContext;
import ca.oshadami.servicedesk.extract.ExtractionService;
import ca.oshadami.servicedesk.extract.ServiceRequestExtraction;
import ca.oshadami.servicedesk.guard.PromptInjectionGuard;
import ca.oshadami.servicedesk.rag.AnswerService;
import ca.oshadami.servicedesk.rag.AskRequest;
import ca.oshadami.servicedesk.rag.AskResponse;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Intake workflow: screen, extract, route, draft a cited reply for the agent, and create a case in the system of
 * record. The AI never sends anything to the resident directly: the draft is for a person to review and send.
 */
@Service
public class TriageService {

    private final PromptInjectionGuard guard;
    private final ExtractionService extraction;
    private final AnswerService answers;
    private final CaseManagementClient cases;
    private final AuditService audit;
    private final CallerContext caller;
    private final AppProperties props;
    private final MeterRegistry meters;

    public TriageService(PromptInjectionGuard guard, ExtractionService extraction, AnswerService answers,
            CaseManagementClient cases, AuditService audit, CallerContext caller, AppProperties props, MeterRegistry meters) {
        this.guard = guard;
        this.extraction = extraction;
        this.answers = answers;
        this.cases = cases;
        this.audit = audit;
        this.caller = caller;
        this.props = props;
        this.meters = meters;
    }

    public TriageResult triage(String text, String channel) {
        long start = System.nanoTime();
        String actor = caller.actor();
        List<String> reasons = new ArrayList<>();

        PromptInjectionGuard.Verdict verdict = guard.inspect(text);
        if (verdict.blocked()) {
            reasons.add("Input screening flagged: " + String.join(", ", verdict.reasons()));
        }

        ServiceRequestExtraction fields = extraction.extract(text);
        if (fields.confidence() < props.extraction().reviewConfidenceThreshold()) {
            reasons.add("Low classification confidence (" + fields.confidence() + ")");
        }
        if (fields.urgency() == ServiceRequestExtraction.Urgency.HIGH) {
            reasons.add("Marked urgent");
        }
        if (fields.requestType().name().equals("COMPLAINT")) {
            reasons.add("Complaints always get a human response");
        }

        AskResponse draft = null;
        if (!verdict.blocked()) {
            // Draft only from public sources: the reply is meant for the resident. The query is the de-identified
            // main question, so names and numbers do not travel into retrieval or the audit trail.
            String query = fields.mainQuestion() != null ? fields.mainQuestion() : fields.summary();
            draft = answers.ask(new AskRequest(query, fields.language()), Set.of(Audience.PUBLIC), actor);
            if (!draft.grounded()) {
                reasons.add("No grounded draft reply available");
            }
        }

        String priority = switch (fields.urgency()) {
            case HIGH -> "P1";
            case NORMAL -> "P3";
            case LOW -> "P4";
        };
        boolean review = !reasons.isEmpty();
        String draftText = draft != null && draft.grounded() ? draft.answer() : null;

        CaseRecord created = cases.create(new CaseRecord(null, fields.requestType().queue(), priority,
                fields.requestType().name(), fields.language(), fields.summary(), review, reasons,
                fields.applicantName(), fields.email(), fields.phone(), fields.referenceNumbers(), draftText,
                channel == null ? "api" : channel, null));

        meters.counter("servicedesk.triage", "queue", created.queue(), "review", String.valueOf(review)).increment();
        audit.record(actor, "triage", "case_created", "case=" + created.caseId() + " type=" + fields.requestType()
                + " queue=" + created.queue() + " review=" + review + " reasons=" + reasons);

        return new TriageResult(created.caseId(), created.queue(), priority, review, reasons, fields, draftText,
                draft == null ? List.of() : draft.sources(), (System.nanoTime() - start) / 1_000_000);
    }
}
