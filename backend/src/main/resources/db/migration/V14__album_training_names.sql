ALTER TABLE albums ADD COLUMN next_training_number BIGINT NOT NULL DEFAULT 0;
ALTER TABLE training_sessions ADD COLUMN album_sequence BIGINT;

-- A separate table avoids MySQL's restriction on reading the updated table in a subquery.
CREATE TABLE training_session_sequence_backfill (
    session_id BIGINT PRIMARY KEY,
    album_sequence BIGINT NOT NULL
);
INSERT INTO training_session_sequence_backfill(session_id,album_sequence)
SELECT s.id, ROW_NUMBER() OVER (PARTITION BY p.album_id ORDER BY s.id)
FROM training_sessions s JOIN photos p ON p.id=s.photo_id;

UPDATE training_sessions SET album_sequence=(
    SELECT b.album_sequence FROM training_session_sequence_backfill b WHERE b.session_id=training_sessions.id
);
UPDATE albums SET next_training_number=COALESCE((
    SELECT MAX(s.album_sequence) FROM training_sessions s JOIN photos p ON p.id=s.photo_id WHERE p.album_id=albums.id
),0);
ALTER TABLE training_sessions MODIFY COLUMN album_sequence BIGINT NOT NULL;
DROP TABLE training_session_sequence_backfill;
