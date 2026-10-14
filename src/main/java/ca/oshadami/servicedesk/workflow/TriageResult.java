package ca.oshadami.servicedesk.workflow;

import java.util.List;

import ca.oshadami.servicedesk.extract.ServiceRequestExtraction;
import ca.oshadami.servicedesk.rag.AskResponse;

/**
 * Outcome of the intake workflow, returned to the caller (UI, n8n, another system).
 */
public record TriageResult(String caseId, String queue, String priority, boolean needsHumanReview,
        List<String> reviewReasons, ServiceRequestExtraction extraction, String draftReply,
        List<AskResponse.Source> draftSources, long latencyMs) {
}
