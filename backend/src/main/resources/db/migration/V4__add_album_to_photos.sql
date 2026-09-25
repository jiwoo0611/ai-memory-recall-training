ALTER TABLE photos
    ADD COLUMN album_id BIGINT NULL;

ALTER TABLE photos
    ADD CONSTRAINT fk_photos_album
        FOREIGN KEY (album_id)
        REFERENCES albums(id);

CREATE INDEX idx_photos_album_created
    ON photos(album_id, created_at);