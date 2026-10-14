package ca.oshadami.servicedesk.workflow;

import java.time.Instant;
import java.util.List;

/**
 * A case as stored in the (mock) downstream case management system.
 */
public record CaseRecord(String caseId, String queue, String priority, String requestType, String language,
        String summary, boolean needsHumanReview, List<String> reviewReasons, String applicantName, String email,
        String phone, List<String> referenceNumbers, String draftReply, String channel, Instant createdAt) {
}
