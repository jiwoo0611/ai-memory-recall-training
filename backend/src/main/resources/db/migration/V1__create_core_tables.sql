CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE photos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    metadata_taken_at DATETIME(6),
    metadata_location VARCHAR(500),
    confirmed_taken_on DATE,
    confirmed_location VARCHAR(500),
    ai_clues TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_photos_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_photos_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_photos_size CHECK (size_bytes > 0)
);
CREATE INDEX idx_photos_owner_created ON photos(user_id, created_at);

CREATE TABLE training_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    photo_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_sessions_photo FOREIGN KEY (photo_id) REFERENCES photos(id),
    CONSTRAINT ck_sessions_status CHECK (status IN ('CREATED', 'GENERATING_QUESTIONS', 'IN_PROGRESS', 'COMPLETED', 'QUESTION_GENERATION_FAILED')),
    CONSTRAINT ck_sessions_completion CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at >= started_at) OR (status <> 'COMPLETED' AND completed_at IS NULL))
);
CREATE INDEX idx_sessions_photo_created ON training_sessions(photo_id, created_at);

CREATE TABLE questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    training_session_id BIGINT NOT NULL,
    question_order INT NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(30) NOT NULL,
    evidence_snapshot TEXT NOT NULL,
    verifiable BOOLEAN NOT NULL,
    reference_information TEXT,
    generation_version VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_questions_session FOREIGN KEY (training_session_id) REFERENCES training_sessions(id),
    CONSTRAINT uk_questions_session_order UNIQUE (training_session_id, question_order),
    CONSTRAINT ck_questions_order CHECK (question_order > 0),
    CONSTRAINT ck_questions_type CHECK (type IN ('PHOTO_CLUE', 'EXPERIENCE', 'CONTEXT', 'INFORMATION_CHECK')),
    CONSTRAINT ck_questions_reference CHECK (verifiable = FALSE OR reference_information IS NOT NULL)
);

CREATE TABLE answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    content TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT uk_answers_question UNIQUE (question_id),
    CONSTRAINT ck_answers_status CHECK (status IN ('ANSWERED', 'CANNOT_RECALL', 'QUESTION_MISMATCH')),
    CONSTRAINT ck_answers_content CHECK (
        (status = 'ANSWERED' AND content IS NOT NULL AND CHAR_LENGTH(TRIM(content)) > 0)
        OR (status IN ('CANNOT_RECALL', 'QUESTION_MISMATCH') AND content IS NULL)
    )
);

CREATE TABLE results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    training_session_id BIGINT NOT NULL,
    feedback_status VARCHAR(30) NOT NULL,
    summary TEXT,
    answered_count INT NOT NULL DEFAULT 0,
    cannot_recall_count INT NOT NULL DEFAULT 0,
    mismatch_count INT NOT NULL DEFAULT 0,
    generation_version VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_results_session FOREIGN KEY (training_session_id) REFERENCES training_sessions(id),
    CONSTRAINT uk_results_session UNIQUE (training_session_id),
    CONSTRAINT ck_results_status CHECK (feedback_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_results_counts CHECK (answered_count >= 0 AND cannot_recall_count >= 0 AND mismatch_count >= 0)
);
