package ca.oshadami.servicedesk.audit;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ca.oshadami.servicedesk.privacy.PiiRedactor;

/**
 * Append-only audit trail of AI interactions and automated actions. Details are redacted before they are stored.
 * Audit failures are logged but never break the user-facing request.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public record AuditEvent(long id, OffsetDateTime occurredAt, String actor, String action, String outcome, String detail) {
    }

    private final JdbcTemplate jdbc;
    private final PiiRedactor redactor;

    public AuditService(JdbcTemplate jdbc, PiiRedactor redactor) {
        this.jdbc = jdbc;
        this.redactor = redactor;
    }

    public void record(String actor, String action, String outcome, String detail) {
        try {
            jdbc.update("INSERT INTO audit_event (actor, action, outcome, detail) VALUES (?, ?, ?, ?)",
                    actor, action, outcome, redactor.forLog(detail, 1000));
        } catch (RuntimeException e) {
            log.error("Could not write audit event action={} outcome={}: {}", action, outcome, e.getMessage());
        }
    }

    public List<AuditEvent> recent(int limit) {
        return jdbc.query(
                "SELECT id, occurred_at, actor, action, outcome, detail FROM audit_event ORDER BY id DESC LIMIT ?",
                (rs, i) -> new AuditEvent(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("actor"), rs.getString("action"), rs.getString("outcome"), rs.getString("detail")),
                Math.min(Math.max(limit, 1), 500));
    }
}
