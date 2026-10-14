package ca.oshadami.servicedesk.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * First-line screen for prompt injection and abuse in user input and uploaded documents (English and French).
 * It is deliberately simple and explainable. The main defences are structural: retrieved text is fenced as data,
 * the system prompt forbids following embedded instructions, and the model has no tools that can change anything.
 */
@Component
public class PromptInjectionGuard {

    public record Verdict(boolean blocked, List<String> reasons) {
        public static Verdict ok() {
            return new Verdict(false, List.of());
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("override-instructions",
                    "(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+|the\\s+)?(previous|prior|above|earlier|your)\\s+(instructions|rules|prompts?|directions)"),
            new Rule("override-instructions-fr",
                    "(ignore[rz]?|oublie[rz]?|ne\\s+tiens?\\s+pas\\s+compte\\s+d)[\\s\\S]{0,20}(instructions|consignes|r[eè]gles)\\s+(pr[eé]c[eé]dentes|ant[eé]rieures|ci-dessus)"),
            new Rule("reveal-system-prompt",
                    "(reveal|show|print|repeat|display|leak)\\s+(me\\s+)?(your|the)\\s+(system\\s+prompt|hidden\\s+instructions|initial\\s+instructions)"),
            new Rule("reveal-system-prompt-fr",
                    "(r[eé]v[eè]le|affiche|montre|r[eé]p[eè]te)[\\s\\S]{0,15}(invite\\s+syst[eè]me|prompt\\s+syst[eè]me|instructions\\s+cach[eé]es)"),
            new Rule("role-hijack",
                    "(you\\s+are\\s+now|act\\s+as|pretend\\s+to\\s+be|from\\s+now\\s+on\\s+you)\\s+(an?\\s+)?(unrestricted|jailbroken|dan|developer\\s+mode|different\\s+assistant)"),
            new Rule("fake-delimiters",
                    "(</?\\s*(system|assistant|sources?)\\s*>|\\[/?(system|inst)\\]|###\\s*system)"));

    private record Rule(String name, Pattern pattern) {
        Rule(String name, String regex) {
            this(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    public Verdict inspect(String text) {
        if (text == null || text.isBlank()) {
            return Verdict.ok();
        }
        List<String> reasons = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                reasons.add(rule.name());
            }
        }
        return reasons.isEmpty() ? Verdict.ok() : new Verdict(true, List.copyOf(reasons));
    }
}
