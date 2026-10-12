package io.github.ronaldobertolucci.unita.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfiguration {
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") String[] origins,
            @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}") String[] methods) {
        if (origins.length == 0 || Arrays.stream(origins).anyMatch(o -> o.isBlank() || o.contains("*")))
            throw new IllegalStateException("Credentialed CORS requires explicit trusted origins");
        var cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOrigins(Arrays.asList(origins));
        cors.setAllowedMethods(Arrays.asList(methods));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-CSRF-Protection"));
        cors.setExposedHeaders(List.of("WWW-Authenticate"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
