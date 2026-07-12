package dev.cairn.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits every request. M5 is transfer mechanics only; the permission model
 * (security doc, section 3) and its wiring into this filter chain, including
 * authenticating Git-over-HTTP requests, is M6's job. Replacing this class is
 * exactly what M6 does, not a change to anything built in M5.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
