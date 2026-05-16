DROP TABLE lobby_reconnect_sessions;

CREATE TABLE lobby_reconnect_sessions (
    session_token_hash TEXT PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE,
    lobby_code TEXT,
    player_display_name TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX lobby_reconnect_sessions_lobby_code_idx
    ON lobby_reconnect_sessions (lobby_code);

CREATE INDEX lobby_reconnect_sessions_expires_at_idx
    ON lobby_reconnect_sessions (expires_at);
