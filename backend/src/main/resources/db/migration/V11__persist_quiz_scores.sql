-- NULL distinguishes legacy/free-text answers and results from scored quizzes.
-- Question content/options/answer_key are immutable, session-local snapshots.
ALTER TABLE answers ADD COLUMN selected_option_index INT;
ALTER TABLE answers ADD COLUMN correct BOOLEAN;
ALTER TABLE answers ADD CONSTRAINT ck_answers_quiz CHECK (
    (selected_option_index IS NULL AND correct IS NULL) OR
    (selected_option_index IS NOT NULL AND correct IS NOT NULL
        AND selected_option_index BETWEEN 0 AND 3 AND status = 'ANSWERED')
);
ALTER TABLE results ADD COLUMN total_count INT;
ALTER TABLE results ADD COLUMN correct_count INT;
ALTER TABLE results ADD COLUMN incorrect_count INT;
ALTER TABLE results ADD CONSTRAINT ck_results_quiz CHECK (
    (total_count IS NULL AND correct_count IS NULL AND incorrect_count IS NULL) OR
    (total_count IS NOT NULL AND correct_count IS NOT NULL AND incorrect_count IS NOT NULL
        AND total_count > 0 AND correct_count >= 0 AND incorrect_count >= 0
        AND correct_count + incorrect_count = total_count AND answered_count = total_count)
);
