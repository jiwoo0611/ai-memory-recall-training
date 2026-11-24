CREATE TABLE delayed_recalls (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_session_id BIGINT NOT NULL,
    condition_name VARCHAR(30) NOT NULL DEFAULT 'QUIZ',
    protocol_version VARCHAR(40) NOT NULL DEFAULT 'FREE_RECALL_7D_V1',
    confirmed_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6),
    submitted_at TIMESTAMP(6),
    response_text TEXT,
    cannot_recall BOOLEAN NOT NULL DEFAULT FALSE,
    draft_version BIGINT NOT NULL DEFAULT 0,
    graded_at TIMESTAMP(6),
    grader_user_id BIGINT,
    CONSTRAINT uk_delayed_source UNIQUE (source_session_id),
    CONSTRAINT fk_delayed_source FOREIGN KEY (source_session_id) REFERENCES training_sessions(id) ON DELETE CASCADE,
    CONSTRAINT ck_delayed_condition CHECK (condition_name IN ('QUIZ'))
);
CREATE TABLE delayed_recall_facts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    delayed_recall_id BIGINT NOT NULL,
    fact_order INT NOT NULL,
    content VARCHAR(500) NOT NULL,
    confirmed_source VARCHAR(30) NOT NULL DEFAULT 'USER_CONFIRMED',
    recalled BOOLEAN,
    evidence_quote VARCHAR(2000),
    grade_note VARCHAR(1000),
    CONSTRAINT uk_delayed_fact_order UNIQUE (delayed_recall_id, fact_order),
    CONSTRAINT fk_delayed_fact FOREIGN KEY (delayed_recall_id) REFERENCES delayed_recalls(id) ON DELETE CASCADE
);
