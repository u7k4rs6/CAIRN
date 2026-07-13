package dev.cairn.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Permits every request; the permission model itself (security doc, section 3) is
 * enforced per-controller via {@code PermissionResolver.authorize}, not this filter
 * chain (see M6's {@code DECISIONS.md} entry on why: the permission logic stays
 * front and center rather than folded into Spring Security's annotation machinery).
 *
 * <p>The CORS configuration is a real, load-bearing fix, not boilerplate: without
 * it, every browser-originated fetch from {@code web/} (not just the gap-closure
 * round's new access-management UI, but the pre-existing {@code MergeBox},
 * {@code ReviewComposer}, and {@code CommentComposer} client islands from M8) is
 * silently blocked by the browser's CORS preflight check. This was never caught
 * before because M8's own verification drove every endpoint with {@code curl},
 * which does not enforce or even perform a CORS preflight the way a real browser
 * does; only exercising the UI in an actual browser (gap-closure audit) surfaced it.
 */
@Configuration
public class SecurityConfig {

    @Value("${cairn.web-origin:http://localhost:3000}")
    private String webOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(webOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
