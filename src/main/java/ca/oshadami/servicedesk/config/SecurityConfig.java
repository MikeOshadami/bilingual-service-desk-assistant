package ca.oshadami.servicedesk.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two modes:
 * <ul>
 * <li>{@code app.security.enabled=true}: OAuth2 resource server validating Keycloak JWTs. Keycloak realm roles
 * become Spring authorities, so the realm role {@code staff} grants ROLE_STAFF.</li>
 * <li>{@code app.security.enabled=false}: quick-start mode. Everyone is anonymous and sees public content only.</li>
 * </ul>
 * Public endpoints (asking questions, submitting a request) stay open in both modes; staff endpoints need ROLE_STAFF.
 */
@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String[] PUBLIC_PATHS = {
            "/", "/index.html", "/app.js", "/app.css", "/favicon.ico",
            "/actuator/health/**", "/actuator/prometheus", "/actuator/info",
            "/mock-case/**", "/error"
    };

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
    SecurityFilterChain securedChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/ask", "/api/requests", "/api/extract").permitAll()
                        .requestMatchers("/api/ingest/**", "/api/audit/**").hasRole("STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/requests/**").hasRole("STAFF")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRoleConverter())));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain openChain(HttpSecurity http) throws Exception {
        log.warn("Security is DISABLED (app.security.enabled=false). All callers are anonymous and see public content only. "
                + "Staff endpoints are open. Use this mode for local development only.");
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Keys are fetched from Keycloak inside the Docker network (jwk-set-uri), while the token's issuer is the
     * browser-facing URL. Validating the issuer explicitly avoids the classic localhost vs container hostname mismatch.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties props, AppProperties app) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.getJwt().getJwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(app.security().expectedIssuer())));
        return decoder;
    }

    static JwtAuthenticationConverter keycloakRoleConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoles);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @SuppressWarnings("unchecked")
    static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            for (Object role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()));
            }
        }
        return authorities;
    }
}
