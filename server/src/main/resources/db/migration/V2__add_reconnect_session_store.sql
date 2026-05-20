CREATE TABLE lobby_reconnect_sessions (
    session_token TEXT PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE,
    lobby_code TEXT,
    player_display_name TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX lobby_reconnect_sessions_lobby_code_idx
    ON lobby_reconnect_sessions (lobby_code);
