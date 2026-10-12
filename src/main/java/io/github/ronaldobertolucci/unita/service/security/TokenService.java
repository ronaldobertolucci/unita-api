package io.github.ronaldobertolucci.unita.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);
    @Value("${api.security.token.secret}")
    private String secret;
    @Value("${api.security.token.issuer}")
    private String issuer;
    @Value("${api.security.token.expiration-hours}")
    private int expirationHours;

    public String generateToken(User user) {
        try {
            var algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer(issuer)
                    .withSubject(user.getEmail())
                    .withClaim("id", user.getId())
                    .withExpiresAt(expirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            logger.error("Error generating JWT token", exception);
            throw new RuntimeException("Error generating JWT token", exception);
        }
    }

    public String getSubject(String tokenJWT) {
        return JWT.require(Algorithm.HMAC256(secret)).withIssuer(issuer).build().verify(tokenJWT).getSubject();
    }

    public Long expiresInSeconds() { return expirationHours * 3600L; }

    public Long getUserIdFromToken(String tokenJWT) {
        return JWT.require(Algorithm.HMAC256(secret)).withIssuer(issuer).build().verify(tokenJWT).getClaim("id").asLong();
    }

    private Instant expirationDate() {
        if (expirationHours <= 0) throw new IllegalStateException("JWT expiration must be positive");
        return Instant.now().plus(java.time.Duration.ofHours(expirationHours));
    }
}
