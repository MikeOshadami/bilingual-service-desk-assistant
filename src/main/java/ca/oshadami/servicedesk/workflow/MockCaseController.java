package ca.oshadami.servicedesk.workflow;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stand-in for an external case management system, so the integration can be demonstrated end to end.
 * In-memory only; restarts clear it.
 */
@RestController
@RequestMapping("/mock-case/api/cases")
public class MockCaseController {

    private final Map<String, CaseRecord> cases = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1000);

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseRecord create(@RequestBody CaseRecord draft) {
        String id = "CASE-" + sequence.incrementAndGet();
        CaseRecord saved = new CaseRecord(id, draft.queue(), draft.priority(), draft.requestType(), draft.language(),
                draft.summary(), draft.needsHumanReview(), draft.reviewReasons(), draft.applicantName(), draft.email(),
                draft.phone(), draft.referenceNumbers(), draft.draftReply(), draft.channel(), Instant.now());
        cases.put(id, saved);
        return saved;
    }

    @GetMapping
    public List<CaseRecord> list() {
        return cases.values().stream().sorted(Comparator.comparing(CaseRecord::createdAt).reversed()).toList();
    }

    @GetMapping("/{id}")
    public CaseRecord get(@PathVariable String id) {
        CaseRecord c = cases.get(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No case " + id);
        }
        return c;
    }
}
