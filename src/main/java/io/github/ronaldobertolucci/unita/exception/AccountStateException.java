package io.github.ronaldobertolucci.unita.exception;

import io.github.ronaldobertolucci.unita.model.user.User;
import org.springframework.security.core.AuthenticationException;

/** Only checked after successful password validation. */
public class AccountStateException extends AuthenticationException {
    private final String code;

    private AccountStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }

    public static void check(User user) {
        if (!user.isEnabled())
            throw new AccountStateException("ACCOUNT_DISABLED", "Account is disabled");
        if (!Boolean.TRUE.equals(user.getEmailVerified()))
            throw new AccountStateException("EMAIL_NOT_VERIFIED", "Email verification is required");
    }
}
