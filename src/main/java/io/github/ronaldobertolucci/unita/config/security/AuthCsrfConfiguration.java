package io.github.ronaldobertolucci.unita.config.security;

import io.github.ronaldobertolucci.unita.controller.AuthenticationController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Set;

@Configuration
public class AuthCsrfConfiguration implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        // Inspect the resolved handler: encoded/normalized routes cannot bypass the guard.
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if (handler instanceof HandlerMethod method && method.getBeanType() == AuthenticationController.class
                        && Set.of("login", "refresh", "logout").contains(method.getMethod().getName())) {
                    response.setHeader("Cache-Control", "no-store");
                    if ("1".equals(request.getHeader("X-CSRF-Protection"))) return true;
                    AuthenticationErrors.write(response, 403, "CSRF_PROTECTION_REQUIRED");
                    return false;
                }
                return true;
            }
        });
    }
}
