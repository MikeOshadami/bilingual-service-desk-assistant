package ca.oshadami.servicedesk.config;

/**
 * Who a knowledge chunk may be shown to. Stored as metadata on every chunk and enforced at retrieval time,
 * so restricted content never reaches the model for a caller who is not allowed to see it.
 */
public enum Audience {
    PUBLIC("public"),
    STAFF("staff");

    private final String value;

    Audience(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Audience from(String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC;
        }
        for (Audience a : values()) {
            if (a.value.equalsIgnoreCase(value.trim())) {
                return a;
            }
        }
        throw new IllegalArgumentException("Unknown audience: " + value);
    }
}
