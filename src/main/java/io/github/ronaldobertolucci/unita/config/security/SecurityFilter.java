package io.github.ronaldobertolucci.unita.config.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final UserRepository repository;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        var paths = PathPatternRequestMatcher.withDefaults();
        return (paths.matcher("/auth/**").matches(request) && !paths.matcher("/auth/me").matches(request))
                || paths.matcher("/password/**").matches(request)
                || paths.matcher("/swagger-ui/**").matches(request)
                || paths.matcher("/swagger-ui.html").matches(request)
                || paths.matcher("/v3/api-docs/**").matches(request);
    }
    @Override protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull FilterChain chain) throws ServletException, IOException {
        String token = getToken(request);
        if (token != null) {
            try {
                String subject = tokenService.getSubject(token);
                var user = subject == null ? null : repository.findByEmailWithRoles(subject).orElse(null);
                if (user == null || !user.isAccessAllowed()) {
                    reject(response, "INVALID_TOKEN"); return;
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            } catch (TokenExpiredException e) {
                reject(response, "SESSION_EXPIRED"); return;
            } catch (JWTVerificationException e) {
                reject(response, "INVALID_TOKEN"); return;
            }
        }
        chain.doFilter(request, response);
    }
    private void reject(HttpServletResponse response, String code) throws IOException {
        SecurityContextHolder.clearContext();
        AuthenticationErrors.write(response, 401, code);
    }
    private String getToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (header.regionMatches(true, 0, "Bearer ", 0, 7)) return header.substring(7).trim();
            return ""; // An unsupported/malformed Authorization header is invalid authentication.
        }
        if (PathPatternRequestMatcher.withDefaults().matcher("/notifications/stream").matches(request))
            return request.getParameter("token");
        return null;
    }
}
