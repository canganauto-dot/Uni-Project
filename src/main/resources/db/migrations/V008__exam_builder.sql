CREATE TABLE exams (
 id INTEGER PRIMARY KEY,
 course_offering_id INTEGER NOT NULL REFERENCES course_offerings(id) ON DELETE RESTRICT,
 title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
 exam_date TEXT NOT NULL CHECK(length(exam_date)=10 AND date(exam_date) IS NOT NULL AND date(exam_date)=exam_date),
 duration_minutes INTEGER NOT NULL CHECK(typeof(duration_minutes)='integer' AND duration_minutes BETWEEN 1 AND 1440),
 intended_marks_hundredths INTEGER NOT NULL CHECK(typeof(intended_marks_hundredths)='integer' AND intended_marks_hundredths BETWEEN 1 AND 10000000),
 instructions TEXT NOT NULL DEFAULT '' CHECK(length(instructions)<=20000),
 status TEXT NOT NULL CHECK(status IN ('DRAFT','FINALIZED','ARCHIVED')),
 created_at TEXT NOT NULL CHECK(julianday(created_at) IS NOT NULL),
 updated_at TEXT NOT NULL CHECK(julianday(updated_at) IS NOT NULL AND julianday(updated_at)>=julianday(created_at))
);
-- @statement
CREATE INDEX idx_exams_offering ON exams(course_offering_id, exam_date);
-- @statement
CREATE TABLE exam_questions (
 exam_id INTEGER NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
 question_id INTEGER NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
 position INTEGER NOT NULL CHECK(typeof(position)='integer' AND position BETWEEN 1 AND 100),
 question_type TEXT NOT NULL CHECK(question_type IN ('MCQ','TRUE_FALSE','SHORT_ANSWER','ESSAY','CODING')),
 question_text TEXT NOT NULL,
 topic TEXT NOT NULL,
 difficulty TEXT NOT NULL CHECK(difficulty IN ('EASY','MEDIUM','HARD')),
 marks_hundredths INTEGER NOT NULL CHECK(typeof(marks_hundredths)='integer' AND marks_hundredths BETWEEN 1 AND 100000),
 answer TEXT NOT NULL,
 explanation TEXT NOT NULL,
 image_path TEXT NOT NULL,
 PRIMARY KEY(exam_id,question_id),
 UNIQUE(exam_id,position)
);
-- @statement
CREATE INDEX idx_exam_questions_source ON exam_questions(question_id);
-- @statement
CREATE TABLE exam_question_choices (
 exam_id INTEGER NOT NULL,
 question_id INTEGER NOT NULL,
 position INTEGER NOT NULL CHECK(position BETWEEN 1 AND 10),
 choice_text TEXT NOT NULL,
 is_correct INTEGER NOT NULL CHECK(is_correct IN (0,1)),
 PRIMARY KEY(exam_id,question_id,position),
 FOREIGN KEY(exam_id,question_id) REFERENCES exam_questions(exam_id,question_id) ON DELETE CASCADE
);
-- @statement
CREATE UNIQUE INDEX idx_exam_one_correct_choice ON exam_question_choices(exam_id,question_id) WHERE is_correct=1;
-- @statement
CREATE TRIGGER exam_question_course_insert BEFORE INSERT ON exam_questions
WHEN (SELECT course_id FROM questions WHERE id=NEW.question_id) <>
 (SELECT o.course_id FROM exams e JOIN course_offerings o ON o.id=e.course_offering_id WHERE e.id=NEW.exam_id)
BEGIN SELECT RAISE(ABORT,'Exam questions must belong to the offering course'); END;
-- @statement
CREATE TRIGGER exam_question_course_update BEFORE UPDATE OF exam_id,question_id ON exam_questions
WHEN (SELECT course_id FROM questions WHERE id=NEW.question_id) <>
 (SELECT o.course_id FROM exams e JOIN course_offerings o ON o.id=e.course_offering_id WHERE e.id=NEW.exam_id)
BEGIN SELECT RAISE(ABORT,'Exam questions must belong to the offering course'); END;
-- @statement
CREATE TRIGGER preserve_exam_offering BEFORE UPDATE OF course_offering_id ON exams
WHEN NEW.course_offering_id<>OLD.course_offering_id
BEGIN SELECT RAISE(ABORT,'Exam offering cannot be changed'); END;
-- @statement
CREATE TRIGGER preserve_offering_exam_context BEFORE UPDATE OF course_id,academic_term_id ON course_offerings
WHEN (NEW.course_id<>OLD.course_id OR NEW.academic_term_id<>OLD.academic_term_id)
 AND EXISTS(SELECT 1 FROM exams WHERE course_offering_id=OLD.id)
BEGIN SELECT RAISE(ABORT,'Offering with exams cannot change course or term'); END;
-- @statement
CREATE TRIGGER preserve_question_exam_course BEFORE UPDATE OF course_id ON questions
WHEN NEW.course_id<>OLD.course_id AND EXISTS(SELECT 1 FROM exam_questions WHERE question_id=OLD.id)
BEGIN SELECT RAISE(ABORT,'A question used in exams cannot change course'); END;
