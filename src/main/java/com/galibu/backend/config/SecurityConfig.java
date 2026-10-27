package com.galibu.backend.config;

import com.galibu.backend.config.RateLimitFilter;
import com.galibu.backend.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Cadena exclusiva del dashboard de monitoreo: login por formulario con
     * un usuario en memoria. No afecta la API (que sigue siendo JWT).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain monitorFilterChain(
        HttpSecurity http,
        @Value("${app.monitor.username:monitor}") String monitorUser,
        @Value("${app.monitor.password:galibu_monitor_2026}") String monitorPassword
    ) throws Exception {
        UserDetails user = User.withUsername(monitorUser)
            .password(new BCryptPasswordEncoder().encode(monitorPassword))
            .roles("MONITOR")
            .build();
        http
            .securityMatcher("/monitor/**", "/api/monitor/**")
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/monitor/login").permitAll()
                .anyRequest().hasRole("MONITOR"))
            .formLogin(form -> form
                .loginPage("/monitor/login")
                .loginProcessingUrl("/monitor/login")
                .defaultSuccessUrl("/monitor", true)
                .failureUrl("/monitor/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/monitor/logout")
                .logoutSuccessUrl("/monitor/login?logout")
                .permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                if (request.getRequestURI().startsWith("/api/monitor")) {
                    response.setStatus(401);
                } else {
                    response.sendRedirect("/monitor/login");
                }
            }))
            .userDetailsService(new InMemoryUserDetailsManager(user));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter, RateLimitFilter rateLimitFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Autenticación requerida\"}");
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/geo/**", "/api/front-logs",
                    "/api/wallet/bold/webhook", "/actuator/health", "/error")
                .permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
