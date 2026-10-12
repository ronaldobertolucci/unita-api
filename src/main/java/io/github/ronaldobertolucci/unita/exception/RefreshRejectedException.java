package io.github.ronaldobertolucci.unita.exception;

/** Expected refresh failure; session revocations must commit despite this exception. */
public class RefreshRejectedException extends RuntimeException {
    public RefreshRejectedException() { super("Refresh session unavailable; sign in again"); }
}
