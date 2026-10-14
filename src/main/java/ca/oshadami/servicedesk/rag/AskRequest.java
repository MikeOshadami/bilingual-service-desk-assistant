package ca.oshadami.servicedesk.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question the resident's question, in English or French
 * @param lang     optional "en" or "fr"; detected from the question when absent
 */
public record AskRequest(@NotBlank @Size(max = 2000) String question, String lang) {
}
