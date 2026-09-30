ALTER TABLE training_sessions ADD COLUMN source_session_id BIGINT;
ALTER TABLE training_sessions ADD COLUMN retry_key VARCHAR(36);
ALTER TABLE training_sessions ADD CONSTRAINT uk_session_retry UNIQUE (source_session_id, retry_key);
ALTER TABLE training_sessions ADD CONSTRAINT fk_session_source FOREIGN KEY (source_session_id) REFERENCES training_sessions(id);
ALTER TABLE training_sessions ADD CONSTRAINT ck_session_retry CHECK (
    (source_session_id IS NULL AND retry_key IS NULL) OR (source_session_id IS NOT NULL AND retry_key IS NOT NULL)
);
