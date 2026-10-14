package ca.oshadami.servicedesk.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight English/French detector. Good enough to pick the reply language for short service questions
 * without calling the model. Callers can always override with an explicit language.
 */
public final class LanguageDetector {

    private static final Set<String> FRENCH = Set.of(
            "le", "la", "les", "un", "une", "des", "du", "de", "et", "est", "je", "j", "vous", "nous", "mon", "ma",
            "mes", "votre", "pour", "avec", "comment", "quand", "quel", "quelle", "quels", "quelles", "pourquoi",
            "est-ce", "que", "qui", "dois", "faut", "permis", "conduire", "immatriculation", "renouveler", "adresse",
            "dans", "sur", "pas", "ne", "au", "aux", "ce", "cette", "puis", "peux", "combien", "frais", "carte");

    private static final Set<String> ENGLISH = Set.of(
            "the", "a", "an", "and", "is", "are", "i", "you", "we", "my", "your", "for", "with", "how", "when",
            "what", "which", "why", "do", "does", "can", "need", "licence", "license", "driver", "vehicle",
            "registration", "renew", "address", "in", "on", "not", "to", "of", "it", "this", "much", "fee", "card");

    private LanguageDetector() {
    }

    /** Returns "fr" or "en". Defaults to "en" when the text gives no signal. */
    public static String detect(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int fr = 0;
        int en = 0;
        for (String token : lower.split("[^\\p{L}'-]+")) {
            for (String part : token.split("'")) {
                if (FRENCH.contains(part)) {
                    fr++;
                }
                if (ENGLISH.contains(part)) {
                    en++;
                }
            }
        }
        // Accented letters are a strong French signal.
        String stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (!stripped.equals(lower)) {
            fr += 2;
        }
        return fr > en ? "fr" : "en";
    }

    public static String normalize(String requested, String text) {
        if (requested != null) {
            String r = requested.trim().toLowerCase(Locale.ROOT);
            if (r.startsWith("fr")) {
                return "fr";
            }
            if (r.startsWith("en")) {
                return "en";
            }
        }
        return detect(text);
    }

    public static String displayName(String lang) {
        return "fr".equals(lang) ? "French (Canadian French)" : "English";
    }
}
