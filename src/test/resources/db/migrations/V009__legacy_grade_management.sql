CREATE TABLE assessment_categories (
 id INTEGER PRIMARY KEY, course_offering_id INTEGER NOT NULL REFERENCES course_offerings(id) ON DELETE RESTRICT,
 name TEXT NOT NULL COLLATE NOCASE CHECK(length(trim(name)) BETWEEN 1 AND 100),
 weight_units INTEGER NOT NULL CHECK(typeof(weight_units)='integer' AND weight_units BETWEEN 0 AND 10000),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
 UNIQUE(course_offering_id,name), UNIQUE(id,course_offering_id)
);
-- @statement
CREATE TABLE assessments (
 id INTEGER PRIMARY KEY, course_offering_id INTEGER NOT NULL REFERENCES course_offerings(id) ON DELETE RESTRICT,
 category_id INTEGER NOT NULL, title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
 maximum_units INTEGER NOT NULL CHECK(typeof(maximum_units)='integer' AND maximum_units BETWEEN 1 AND 10000000),
 assessment_date TEXT NOT NULL CHECK(length(assessment_date)=10 AND date(assessment_date,'+0 days')=assessment_date),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
 FOREIGN KEY(category_id,course_offering_id) REFERENCES assessment_categories(id,course_offering_id) ON DELETE RESTRICT,
 UNIQUE(id,course_offering_id)
);
-- @statement
CREATE INDEX assessment_offering_date ON assessments(course_offering_id,assessment_date,id);
-- @statement
CREATE UNIQUE INDEX enrollment_offering_identity ON enrollments(id,course_offering_id);
-- @statement
CREATE TABLE student_grades (
 enrollment_id INTEGER NOT NULL, assessment_id INTEGER NOT NULL, course_offering_id INTEGER NOT NULL,
 score_units INTEGER NOT NULL CHECK(typeof(score_units)='integer' AND score_units>=0),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
 PRIMARY KEY(enrollment_id,assessment_id),
 FOREIGN KEY(enrollment_id,course_offering_id) REFERENCES enrollments(id,course_offering_id) ON DELETE RESTRICT,
 FOREIGN KEY(assessment_id,course_offering_id) REFERENCES assessments(id,course_offering_id) ON DELETE CASCADE
);
-- @statement
CREATE INDEX grades_assessment ON student_grades(assessment_id);
-- @statement
CREATE TRIGGER grade_range_insert BEFORE INSERT ON student_grades
WHEN NEW.score_units > (SELECT maximum_units FROM assessments WHERE id=NEW.assessment_id)
BEGIN SELECT RAISE(ABORT,'Grade exceeds assessment maximum'); END;
-- @statement
CREATE TRIGGER grade_range_update BEFORE UPDATE ON student_grades
WHEN NEW.score_units > (SELECT maximum_units FROM assessments WHERE id=NEW.assessment_id)
BEGIN SELECT RAISE(ABORT,'Grade exceeds assessment maximum'); END;
-- @statement
CREATE TRIGGER assessment_maximum_update BEFORE UPDATE OF maximum_units ON assessments
WHEN EXISTS(SELECT 1 FROM student_grades WHERE assessment_id=OLD.id AND score_units>NEW.maximum_units)
BEGIN SELECT RAISE(ABORT,'Grade exceeds assessment maximum'); END;
-- @statement
CREATE TRIGGER offering_grade_history BEFORE UPDATE OF course_id,academic_term_id ON course_offerings
WHEN (NEW.course_id<>OLD.course_id OR NEW.academic_term_id<>OLD.academic_term_id)
 AND EXISTS(SELECT 1 FROM assessment_categories WHERE course_offering_id=OLD.id)
BEGIN SELECT RAISE(ABORT,'Offering with grading history cannot be reassigned'); END;
-- @statement
CREATE TABLE grading_scales (
 course_offering_id INTEGER PRIMARY KEY REFERENCES course_offerings(id) ON DELETE RESTRICT,
 a_units INTEGER NOT NULL, b_units INTEGER NOT NULL, c_units INTEGER NOT NULL, d_units INTEGER NOT NULL,
 pass_units INTEGER NOT NULL CHECK(pass_units BETWEEN 0 AND 10000),
 CHECK(10000>=a_units AND a_units>b_units AND b_units>c_units AND c_units>d_units AND d_units>=0)
);
