package ca.oshadami.servicedesk.rag;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts which numbered sources the model actually cited, ignoring numbers that do not match a retrieved source.
 */
final class CitationParser {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})\\]");

    private CitationParser() {
    }

    static Set<Integer> cited(String answer, int sourceCount) {
        Set<Integer> result = new TreeSet<>();
        if (answer == null) {
            return result;
        }
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= sourceCount) {
                result.add(n);
            }
        }
        return result;
    }

    /** Removes citation markers that point at sources that were never retrieved (model hallucinated numbers). */
    static String stripInvalid(String answer, int sourceCount) {
        Matcher m = CITATION.matcher(answer);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            m.appendReplacement(sb, n >= 1 && n <= sourceCount ? Matcher.quoteReplacement(m.group()) : "");
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
