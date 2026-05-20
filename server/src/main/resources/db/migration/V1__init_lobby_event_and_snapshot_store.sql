CREATE TABLE lobby_events (
    id BIGSERIAL PRIMARY KEY,
    lobby_code TEXT NOT NULL,
    state_version BIGINT NOT NULL,
    turn_count INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    event_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT lobby_events_state_version_non_negative CHECK (state_version >= 0),
    CONSTRAINT lobby_events_turn_count_non_negative CHECK (turn_count >= 0),
    CONSTRAINT lobby_events_lobby_code_state_version_unique UNIQUE (lobby_code, state_version)
);

CREATE INDEX lobby_events_lobby_code_state_version_desc_idx
    ON lobby_events (lobby_code, state_version DESC);

CREATE INDEX lobby_events_lobby_code_turn_count_desc_idx
    ON lobby_events (lobby_code, turn_count DESC);

CREATE TABLE lobby_snapshots (
    id BIGSERIAL PRIMARY KEY,
    lobby_code TEXT NOT NULL,
    state_version BIGINT NOT NULL,
    turn_count INTEGER NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT lobby_snapshots_state_version_non_negative CHECK (state_version >= 0),
    CONSTRAINT lobby_snapshots_turn_count_non_negative CHECK (turn_count >= 0),
    CONSTRAINT lobby_snapshots_lobby_code_state_version_unique UNIQUE (lobby_code, state_version)
);

CREATE INDEX lobby_snapshots_lobby_code_state_version_desc_idx
    ON lobby_snapshots (lobby_code, state_version DESC);

CREATE INDEX lobby_snapshots_lobby_code_turn_count_desc_idx
    ON lobby_snapshots (lobby_code, turn_count DESC);
