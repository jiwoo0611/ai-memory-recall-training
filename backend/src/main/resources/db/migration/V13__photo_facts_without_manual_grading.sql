ALTER TABLE delayed_recall_facts ADD COLUMN photo_id BIGINT;
ALTER TABLE delayed_recall_facts ADD CONSTRAINT fk_delayed_fact_photo
    FOREIGN KEY (photo_id) REFERENCES photos(id) ON DELETE SET NULL;
-- Keep historical facts, scores and timestamps. Only the retired account link is removed.
ALTER TABLE delayed_recalls DROP COLUMN grader_user_id;
