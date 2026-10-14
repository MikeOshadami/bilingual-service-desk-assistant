package ca.oshadami.servicedesk.config;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the current caller's identity and the knowledge audiences they may read.
 */
@Component
public class CallerContext {

    public static final String STAFF_AUTHORITY = "ROLE_STAFF";

    public boolean isStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(STAFF_AUTHORITY::equals);
    }

    /** A stable, non-personal identifier for audit records. */
    public String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String user = jwt.getClaimAsString("preferred_username");
            return user != null ? user : jwt.getSubject();
        }
        return "anonymous";
    }

    public Set<Audience> allowedAudiences() {
        return isStaff() ? Set.of(Audience.PUBLIC, Audience.STAFF) : Set.of(Audience.PUBLIC);
    }

    /** Spring AI portable filter expression restricting retrieval to the caller's audiences. */
    public String audienceFilter() {
        return audienceFilter(allowedAudiences());
    }

    public static String audienceFilter(Set<Audience> audiences) {
        String values = audiences.stream()
                .map(a -> "'" + a.value() + "'")
                .sorted()
                .collect(Collectors.joining(", "));
        return "audience in [" + values + "]";
    }
}
