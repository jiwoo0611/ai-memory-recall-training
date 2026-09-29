-- Clear previously generated quiz data for the current test account only.
-- Photos, albums, metadata, AI clues, and other users' quiz data are preserved.
DELETE FROM answers
WHERE question_id IN (
    SELECT q.id
    FROM questions q
    JOIN training_sessions ts ON ts.id = q.training_session_id
    JOIN photos p ON p.id = ts.photo_id
    JOIN users u ON u.id = p.user_id
    WHERE u.email = 'test@1234.com'
);

DELETE FROM results
WHERE training_session_id IN (
    SELECT ts.id
    FROM training_sessions ts
    JOIN photos p ON p.id = ts.photo_id
    JOIN users u ON u.id = p.user_id
    WHERE u.email = 'test@1234.com'
);

DELETE FROM questions
WHERE training_session_id IN (
    SELECT ts.id
    FROM training_sessions ts
    JOIN photos p ON p.id = ts.photo_id
    JOIN users u ON u.id = p.user_id
    WHERE u.email = 'test@1234.com'
);

DELETE FROM training_sessions
WHERE photo_id IN (
    SELECT p.id
    FROM photos p
    JOIN users u ON u.id = p.user_id
    WHERE u.email = 'test@1234.com'
);