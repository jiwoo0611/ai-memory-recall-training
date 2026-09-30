-- Keep retry identity outside the session chain so deleting a photo does not
-- depend on the database's maximum recursive cascade depth.
CREATE TABLE training_retries (
    source_session_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    target_session_id BIGINT NOT NULL,
    PRIMARY KEY (source_session_id, request_id),
    CONSTRAINT uk_retry_target UNIQUE (target_session_id),
    CONSTRAINT fk_retry_source FOREIGN KEY (source_session_id) REFERENCES training_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_retry_target FOREIGN KEY (target_session_id) REFERENCES training_sessions(id) ON DELETE CASCADE
);
INSERT INTO training_retries (source_session_id, request_id, target_session_id)
SELECT source_session_id, retry_key, id FROM training_sessions WHERE source_session_id IS NOT NULL;
UPDATE training_sessions SET source_session_id = NULL, retry_key = NULL;
-- V8's nullable columns are retained for migration compatibility; application
-- writes use training_retries exclusively from this version onward.
