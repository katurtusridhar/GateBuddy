-- ==============================================================================
-- GATE CSE COMPANION — STAGING MIGRATION: DURABLE SESSION REVOCATION
-- Stores cryptographic fingerprints of revoked sessions with strict RLS.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.revoked_sessions (
    session_fingerprint TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_revoked_sessions_expires_at ON public.revoked_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_revoked_sessions_username ON public.revoked_sessions(username);

-- Enable Row Level Security (RLS)
ALTER TABLE public.revoked_sessions ENABLE ROW LEVEL SECURITY;

-- Deny all access to public, anon, and authenticated roles.
-- Browser clients cannot read, insert, update, or delete revocation records directly.
REVOKE ALL ON TABLE public.revoked_sessions FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE public.revoked_sessions TO service_role;
