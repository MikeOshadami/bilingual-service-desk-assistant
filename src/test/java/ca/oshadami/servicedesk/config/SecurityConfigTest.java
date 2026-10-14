package ca.oshadami.servicedesk.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {

    @Test
    void mapsKeycloakRealmRolesToAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("staff", "offline_access")))
                .build();
        assertThat(SecurityConfig.realmRoles(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_OFFLINE_ACCESS");
    }

    @Test
    void publicCallersOnlyGetPublicFilter() {
        assertThat(CallerContext.audienceFilter(Set.of(Audience.PUBLIC))).isEqualTo("audience in ['public']");
        assertThat(CallerContext.audienceFilter(Set.of(Audience.PUBLIC, Audience.STAFF)))
                .isEqualTo("audience in ['public', 'staff']");
    }
}
