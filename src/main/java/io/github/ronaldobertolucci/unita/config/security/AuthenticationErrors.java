package io.github.ronaldobertolucci.unita.config.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class AuthenticationErrors {
    private AuthenticationErrors() {}
    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\"}");
    }
}
