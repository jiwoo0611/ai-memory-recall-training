CREATE TABLE revoked_tokens (
    jti VARCHAR(255) PRIMARY KEY,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_revoked_tokens_jti CHECK (CHAR_LENGTH(jti) > 0)
);
CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens (expires_at);
