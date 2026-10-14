package ca.oshadami.servicedesk.rag;

import java.util.List;

/**
 * @param answer      the reply shown to the resident
 * @param lang        language of the reply
 * @param grounded    true when the answer cites at least one retrieved source
 * @param status      ANSWERED, NO_ANSWER (nothing relevant found) or BLOCKED (failed input screening)
 * @param sources     retrieved sources, numbered as cited in the answer
 * @param latencyMs   end-to-end time
 */
public record AskResponse(String answer, String lang, boolean grounded, Status status, List<Source> sources, long latencyMs) {

    public enum Status {
        ANSWERED, NO_ANSWER, BLOCKED
    }

    public record Source(int n, String sourceId, String title, String url, String lang, String audience, double score,
            boolean cited, String excerpt) {
    }
}
