package com.glitchbytez.splitwise.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless security: no sessions, no CSRF (no cookies are used), no form/basic login.
 * Only the documentation and health endpoints are public; everything else needs authentication.
 * The JWT filter and the public auth endpoints are added in Phase 1.
 * <p>
 * 401/403 raised inside the filter chain are forwarded to the MVC exception resolver so they are
 * rendered as the same {@code ProblemDetail} body as every other error.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                exceptionResolver.resolveException(request, response, null, authException))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                exceptionResolver.resolveException(request, response, null, accessDeniedException)))
                .build();
    }
}
