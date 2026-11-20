package com.galibu.backend.security;

import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseValidAccessToken(token);
                String uid = claims.getSubject();
                // El JWT ya incluye el rol. Se usa directamente para evitar una
                // consulta a la base de datos en cada request (latencia). Solo se
                // consulta la BD si el token no trae rol (tokens antiguos).
                String role = claims.get("role", String.class);
                if (role == null || role.isBlank()) {
                    try {
                        User user = users.findById(uid).orElse(null);
                        if (user != null) {
                            role = user.getRole();
                        }
                    } catch (Exception ignored) {
                    }
                }
                var authorities = role == null
                    ? List.<SimpleGrantedAuthority>of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var auth = new UsernamePasswordAuthenticationToken(
                    uid, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
