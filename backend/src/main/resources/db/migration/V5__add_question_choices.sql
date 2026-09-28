ALTER TABLE questions ADD COLUMN format VARCHAR(30) NOT NULL DEFAULT 'FREE_TEXT';
ALTER TABLE questions ADD COLUMN options_json TEXT;
ALTER TABLE questions ADD COLUMN answer_key TEXT;

ALTER TABLE questions
    ADD CONSTRAINT ck_questions_format
        CHECK (format IN ('MULTIPLE_CHOICE', 'FREE_TEXT'));
