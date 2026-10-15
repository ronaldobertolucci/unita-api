package io.github.ronaldobertolucci.unita.validation;

import java.util.Locale;

/** Format validation only: historical and new CNPJs do not require check-digit validation. */
public final class Cnpj {
    public static final String PATTERN = "[A-Z0-9]{12}[0-9]{2}";
    public static final String INPUT_PATTERN = "^(?:[A-Za-z0-9]{12}[0-9]{2}|[A-Za-z0-9]{2}\\.[A-Za-z0-9]{3}\\.[A-Za-z0-9]{3}/[A-Za-z0-9]{4}-[0-9]{2})$";
    private Cnpj() {}

    public static String normalize(String value) {
        if (value == null) return null;
        // Accept the canonical value or the complete conventional mask, never arbitrary punctuation.
        if (value.matches(INPUT_PATTERN)) {
            return value.replace(".", "").replace("/", "").replace("-", "").toUpperCase(Locale.ROOT);
        }
        return value;
    }

    public static String requireValid(String value) {
        String canonical = normalize(value);
        if (canonical == null || !canonical.matches(PATTERN)) {
            throw new IllegalArgumentException("CNPJ must contain 12 letters/digits and 2 final digits, optionally in the complete mask XX.XXX.XXX/XXXX-XX");
        }
        return canonical;
    }
}
