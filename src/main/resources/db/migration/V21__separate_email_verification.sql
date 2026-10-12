-- Preserve access for previously enabled accounts, including operator-created admins:
-- the old login required only enabled=true, and did not retain separate verification state.
-- A consumed confirmation token is positive evidence even for disabled accounts.
-- Pending/expired tokens and absence of tokens are never grounds to enable an account.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;

UPDATE users
SET email_verified = true
WHERE enabled = true
   OR EXISTS (SELECT 1 FROM email_verification_tokens t WHERE t.user_id = users.id AND t.used = true);

-- enabled is intentionally untouched. Disabled legacy accounts are ambiguous and need
-- administrative review; see docs/auth-account-state.md. This is a forward Flyway migration.
