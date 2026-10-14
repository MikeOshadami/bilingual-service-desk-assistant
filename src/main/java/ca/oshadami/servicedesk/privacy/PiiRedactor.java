package ca.oshadami.servicedesk.privacy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Masks common Canadian personal identifiers before text is written to logs, audit records or metrics.
 * Pattern-based redaction is a safety net, not a guarantee; the design keeps raw personal data out of logs
 * by redacting at the boundary rather than trusting every log statement.
 */
@Component
public class PiiRedactor {

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put("EMAIL", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
        // Card-like numbers first so they are not partly matched as phone numbers.
        PATTERNS.put("CARD", Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b"));
        // Social Insurance Number: 9 digits, often grouped 3-3-3.
        PATTERNS.put("SIN", Pattern.compile("\\b\\d{3}[ -]?\\d{3}[ -]?\\d{3}\\b"));
        PATTERNS.put("PHONE", Pattern.compile("(?:\\+?1[ .-]?)?\\(?\\b\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}\\b"));
        // Canadian postal code, e.g. E1A 2B3. New Brunswick codes start with E.
        PATTERNS.put("POSTAL_CODE", Pattern.compile("\\b[A-CEGHJ-NPR-TVXY]\\d[A-CEGHJ-NPR-TV-Z][ -]?\\d[A-CEGHJ-NPR-TV-Z]\\d\\b",
                Pattern.CASE_INSENSITIVE));
        // Licence, plate or account style identifiers: 7+ digit runs not caught above.
        PATTERNS.put("ID_NUMBER", Pattern.compile("\\b\\d{7,}\\b"));
    }

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            Matcher m = entry.getValue().matcher(result);
            result = m.replaceAll("[" + entry.getKey() + "]");
        }
        return result;
    }

    /** Redacts and truncates, for log lines and audit details. */
    public String forLog(String text, int maxChars) {
        String redacted = redact(text);
        if (redacted == null) {
            return null;
        }
        String oneLine = redacted.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= maxChars ? oneLine : oneLine.substring(0, maxChars) + "...";
    }
}
