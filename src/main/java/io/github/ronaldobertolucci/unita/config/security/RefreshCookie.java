package io.github.ronaldobertolucci.unita.config.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;

@Component
public class RefreshCookie {
    public static final String NAME = "unita_refresh";
    @Value("${api.security.refresh.cookie.secure:false}") private boolean secure;
    @Value("${api.security.refresh.cookie.same-site:Lax}") private String sameSite;
    @PostConstruct
    void validate() {
        if (!java.util.Set.of("Strict", "Lax", "None").contains(sameSite) || (sameSite.equals("None") && !secure))
            throw new IllegalStateException("SameSite=None requires Secure/HTTPS; use Lax or Strict for local HTTP");
    }
    public String read(HttpServletRequest request) {
        if (request.getCookies() != null)
            for (var cookie : request.getCookies()) if (NAME.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    public String issue(HttpServletRequest request, String token, Instant expiresAt) {
        return build(request, token, Duration.ofSeconds(Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds())));
    }
    public String clear(HttpServletRequest request) { return build(request, "", Duration.ZERO); }
    private String build(HttpServletRequest request, String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite(sameSite)
                .path(request.getContextPath() + "/auth").maxAge(maxAge).build().toString();
    }
}
