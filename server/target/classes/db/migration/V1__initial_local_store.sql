CREATE TABLE puzzles (
    puzzle_id VARCHAR(36) PRIMARY KEY,
    columns_count INT NOT NULL CHECK (columns_count > 0 AND columns_count <= 240),
    rows_count INT NOT NULL CHECK (rows_count > 0 AND rows_count <= 240),
    region_ids CLOB NOT NULL,
    givens CLOB NOT NULL,
    solution CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE attempts (
    attempt_id VARCHAR(36) PRIMARY KEY,
    puzzle_id VARCHAR(36) NOT NULL REFERENCES puzzles (puzzle_id),
    values_snapshot CLOB NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('READY', 'PLAYING', 'SUSPENDED', 'COMPLETED')),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    editor_session_id VARCHAR(128) NOT NULL,
    editor_lease_expires_at TIMESTAMP WITH TIME ZONE,
    move_count INT NOT NULL DEFAULT 0 CHECK (move_count >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE creation_receipts (
    request_id VARCHAR(128) PRIMARY KEY,
    payload_hash CHAR(64) NOT NULL,
    attempt_id VARCHAR(36) NOT NULL UNIQUE REFERENCES attempts (attempt_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE command_receipts (
    attempt_id VARCHAR(36) NOT NULL REFERENCES attempts (attempt_id),
    command_id VARCHAR(128) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    response_json CLOB NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attempt_id, command_id)
);

CREATE TABLE results (
    result_id VARCHAR(36) PRIMARY KEY,
    attempt_id VARCHAR(36) NOT NULL UNIQUE REFERENCES attempts (attempt_id),
    values_snapshot CLOB NOT NULL,
    move_count INT NOT NULL CHECK (move_count >= 0),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    player_tag VARCHAR(32)
);

CREATE INDEX attempts_updated_at_idx ON attempts (updated_at);
CREATE INDEX results_completed_at_idx ON results (completed_at);
