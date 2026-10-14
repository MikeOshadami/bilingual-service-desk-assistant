package ca.oshadami.servicedesk.extract;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Structured fields pulled from a free-text request or an uploaded document. The JSON schema generated from this
 * record is sent to Ollama as the required output format, so the model can only answer in this shape.
 */
public record ServiceRequestExtraction(
        @NotNull @JsonPropertyDescription("Best matching category for the request") RequestType requestType,
        @NotNull @JsonPropertyDescription("Language the requester wrote in: en or fr") String language,
        @JsonPropertyDescription("Requester's full name if stated, otherwise null") String applicantName,
        @JsonPropertyDescription("Requester's email if stated, otherwise null") String email,
        @JsonPropertyDescription("Requester's phone number if stated, otherwise null") String phone,
        @JsonPropertyDescription("Reference, file, licence or plate numbers mentioned, exactly as written") List<String> referenceNumbers,
        @NotNull @Size(max = 600) @JsonPropertyDescription("Neutral one or two sentence summary, in English, of only what the person wrote. Do not add requirements or advice") String summary,
        @JsonPropertyDescription("The person's main question as one short general question, in the language they wrote in, with no names, numbers or other personal details") String mainQuestion,
        @NotNull @JsonPropertyDescription("HIGH only for safety issues, imminent deadlines or explicit urgency; LOW for general information") Urgency urgency,
        @JsonPropertyDescription("Information a clerk would need that is missing from the request") List<String> missingInformation,
        @DecimalMin("0.0") @DecimalMax("1.0") @JsonPropertyDescription("Confidence in the category between 0 and 1") double confidence) {

    public enum Urgency {
        LOW, NORMAL, HIGH
    }
}
