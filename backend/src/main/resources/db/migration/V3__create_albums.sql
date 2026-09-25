CREATE TABLE albums (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT fk_albums_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
    );

    CREATE INDEX idx_albums_user_created
        ON albums(user_id, created_at);