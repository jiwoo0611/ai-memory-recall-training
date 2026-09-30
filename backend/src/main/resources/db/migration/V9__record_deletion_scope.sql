ALTER TABLE answers DROP FOREIGN KEY fk_answers_question;
ALTER TABLE answers ADD CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE;
ALTER TABLE results DROP FOREIGN KEY fk_results_session;
ALTER TABLE results ADD CONSTRAINT fk_results_session FOREIGN KEY (training_session_id) REFERENCES training_sessions(id) ON DELETE CASCADE;
ALTER TABLE questions DROP FOREIGN KEY fk_questions_session;
ALTER TABLE questions ADD CONSTRAINT fk_questions_session FOREIGN KEY (training_session_id) REFERENCES training_sessions(id) ON DELETE CASCADE;
ALTER TABLE training_sessions DROP FOREIGN KEY fk_session_source;
ALTER TABLE training_sessions ADD CONSTRAINT fk_session_source FOREIGN KEY (source_session_id) REFERENCES training_sessions(id) ON DELETE CASCADE;
ALTER TABLE training_sessions DROP FOREIGN KEY fk_sessions_photo;
ALTER TABLE training_sessions ADD CONSTRAINT fk_sessions_photo FOREIGN KEY (photo_id) REFERENCES photos(id) ON DELETE CASCADE;
CREATE TABLE storage_deletions (
    storage_key VARCHAR(500) PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL
);
