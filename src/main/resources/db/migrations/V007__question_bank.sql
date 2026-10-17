CREATE TABLE questions (
    id INTEGER PRIMARY KEY,
    course_id INTEGER NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    question_type TEXT NOT NULL CHECK (question_type IN ('MCQ','TRUE_FALSE','SHORT_ANSWER','ESSAY','CODING')),
    question_text TEXT NOT NULL CHECK (length(trim(question_text)) BETWEEN 1 AND 20000),
    topic TEXT NOT NULL DEFAULT '' CHECK (length(topic) <= 200),
    difficulty TEXT NOT NULL CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    marks_hundredths INTEGER NOT NULL CHECK (typeof(marks_hundredths) = 'integer' AND marks_hundredths BETWEEN 1 AND 100000),
    answer TEXT NOT NULL DEFAULT '' CHECK (length(answer) <= 20000),
    explanation TEXT NOT NULL DEFAULT '' CHECK (length(explanation) <= 20000),
    image_path TEXT NOT NULL DEFAULT '' CHECK (length(image_path) <= 2000),
    correct_choice_position INTEGER,
    usage_count INTEGER NOT NULL DEFAULT 0 CHECK (typeof(usage_count) = 'integer' AND usage_count >= 0),
    last_used_date TEXT CHECK (last_used_date IS NULL OR (length(last_used_date) = 10 AND date(last_used_date) IS NOT NULL AND date(last_used_date) = last_used_date)),
    created_at TEXT NOT NULL CHECK (julianday(created_at) IS NOT NULL),
    updated_at TEXT NOT NULL CHECK (julianday(updated_at) IS NOT NULL AND julianday(updated_at) >= julianday(created_at)),
    CHECK ((question_type = 'MCQ' AND correct_choice_position IS NOT NULL) OR (question_type <> 'MCQ' AND correct_choice_position IS NULL)),
    CHECK (question_type <> 'TRUE_FALSE' OR answer IN ('TRUE','FALSE')),
    FOREIGN KEY (id, correct_choice_position) REFERENCES question_choices(question_id, position) DEFERRABLE INITIALLY DEFERRED
);
-- @statement
CREATE TABLE question_choices (
    question_id INTEGER NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (typeof(position) = 'integer' AND position BETWEEN 1 AND 10),
    choice_text TEXT NOT NULL CHECK (length(trim(choice_text)) BETWEEN 1 AND 2000),
    PRIMARY KEY (question_id, position)
);
-- @statement
CREATE INDEX idx_questions_course_type_difficulty ON questions(course_id, question_type, difficulty);
-- @statement
CREATE TRIGGER choices_require_mcq_insert BEFORE INSERT ON question_choices
WHEN (SELECT question_type FROM questions WHERE id = NEW.question_id) <> 'MCQ'
BEGIN
    SELECT RAISE(ABORT, 'Answer choices require an MCQ question');
END;
-- @statement
CREATE TRIGGER choices_require_mcq_update BEFORE UPDATE ON question_choices
WHEN (SELECT question_type FROM questions WHERE id = NEW.question_id) <> 'MCQ'
BEGIN
    SELECT RAISE(ABORT, 'Answer choices require an MCQ question');
END;
-- @statement
CREATE TRIGGER non_mcq_has_no_choices BEFORE UPDATE OF question_type ON questions
WHEN NEW.question_type <> 'MCQ' AND EXISTS (SELECT 1 FROM question_choices WHERE question_id = OLD.id)
BEGIN
    SELECT RAISE(ABORT, 'Remove answer choices before changing question type');
END;
