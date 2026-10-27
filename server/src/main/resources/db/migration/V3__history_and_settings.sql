ALTER TABLE puzzles ADD generation_json CLOB;
CREATE TABLE history_entries (
    record_id VARCHAR(36) PRIMARY KEY,
    attempt_id VARCHAR(36) UNIQUE REFERENCES attempts(attempt_id),
    source_attempt_id VARCHAR(36),
    map_size VARCHAR(64) NOT NULL,
    completed_millis BIGINT,
    elapsed_millis BIGINT CHECK (elapsed_millis >= 0),
    player_tag VARCHAR(128),
    scoring_version VARCHAR(32) NOT NULL,
    value_entries INT,
    legacy_accuracy INT,
    total_clicks BIGINT,
    total_miss_clicks BIGINT,
    progress_json CLOB,
    puzzle_settings_json CLOB,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO history_entries(record_id,attempt_id,map_size,completed_millis,player_tag,
                            scoring_version,value_entries,progress_json)
SELECT r.result_id,r.attempt_id,CONCAT(p.columns_count,'x',p.rows_count),
       DATEDIFF('MILLISECOND', TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00', r.completed_at),
       r.player_tag,CASE WHEN r.progress_json IS NULL THEN 'unrecorded-v0' ELSE 'direct-entry-v1' END,
       r.move_count,r.progress_json
FROM results r JOIN attempts a ON a.attempt_id=r.attempt_id JOIN puzzles p ON p.puzzle_id=a.puzzle_id;

CREATE TABLE local_settings (
    singleton_id INT PRIMARY KEY CHECK (singleton_id=1),
    revision BIGINT NOT NULL DEFAULT 0,
    settings_json CLOB NOT NULL
);
INSERT INTO local_settings(singleton_id,settings_json) VALUES
(1,'{"locale":"en","theme":"dark","reducedDetail":false,"showConflicts":false,"preset":"7x5","maxRegionSize":5,"difficulty":"EASY","playerTag":""}');

CREATE TABLE history_imports (
    fingerprint CHAR(64) PRIMARY KEY,
    raw_source CLOB NOT NULL,
    imported_count INT NOT NULL,
    rejected_json CLOB NOT NULL,
    restore_settings BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
