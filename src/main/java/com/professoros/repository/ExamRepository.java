package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class ExamRepository {
    private final JdbcOperations jdbc;
    public ExamRepository(Database database) { jdbc=new JdbcOperations(database,"A question or position is duplicated in this exam."); }

    public Exam save(long offeringId,Long examId,ExamInput input,List<Long> questionIds,Instant now) {
        return jdbc.transaction(c -> {
            long courseId=course(c,offeringId);
            Exam previous=examId==null ? null : required(c,offeringId,examId);
            if(previous!=null) { draft(previous); }
            Instant timestamp=previous!=null && now.isBefore(previous.updatedAt()) ? previous.updatedAt() : now;
            Map<Long,ExamQuestion> preserved=new HashMap<>();
            if(previous!=null) { previous.questions().forEach(q -> preserved.put(q.questionId(),q)); }
            List<ExamQuestion> snapshots=new ArrayList<>();
            for(long questionId:questionIds) {
                Question bank=QuestionRepository.read(c,questionId).orElseThrow(() -> new DomainValidationException("A selected bank question no longer exists. Refresh and retry."));
                if(bank.courseId()!=courseId) { throw new DomainValidationException("Every exam question must belong to the offering's catalog course."); }
                int position=snapshots.size()+1;
                snapshots.add(preserved.containsKey(questionId) ? preserved.get(questionId).atPosition(position) : ExamQuestion.snapshot(bank,position));
            }
            long id;
            if(previous==null) {
                try(var s=c.prepareStatement("""
                        INSERT INTO exams(course_offering_id,title,exam_date,duration_minutes,intended_marks_hundredths,instructions,status,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,'DRAFT',?,?) RETURNING id
                        """)) {
                    JdbcOperations.bind(s,new Object[]{offeringId,input.title(),input.date(),input.durationMinutes(),hundredths(input.intendedMarks()),input.instructions(),timestamp,timestamp});
                    try(var r=s.executeQuery()) { r.next(); id=r.getLong(1); }
                }
            } else {
                id=previous.id();
                execute(c,"UPDATE exams SET title=?,exam_date=?,duration_minutes=?,intended_marks_hundredths=?,instructions=?,updated_at=? WHERE id=?",
                        input.title(),input.date(),input.durationMinutes(),hundredths(input.intendedMarks()),input.instructions(),timestamp,id);
                execute(c,"DELETE FROM exam_questions WHERE exam_id=?",id);
            }
            for(ExamQuestion q:snapshots) {
                execute(c,"""
                        INSERT INTO exam_questions(exam_id,question_id,position,question_type,question_text,topic,difficulty,marks_hundredths,answer,explanation,image_path)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        """,id,q.questionId(),q.position(),q.type().name(),q.text(),q.topic(),q.difficulty().name(),hundredths(q.marks()),q.answer(),q.explanation(),q.imagePath());
                int choicePosition=0;
                for(AnswerChoice choice:q.choices()) {
                    execute(c,"INSERT INTO exam_question_choices(exam_id,question_id,position,choice_text,is_correct) VALUES (?,?,?,?,?)",
                            id,q.questionId(),++choicePosition,choice.text(),choice.correct()?1:0);
                }
            }
            return required(c,offeringId,id);
        });
    }
    public Optional<Exam> findById(long offeringId,long id) { return jdbc.transaction(c -> read(c,offeringId,id)); }
    public List<Exam> findByOffering(long offeringId) {
        return jdbc.transaction(c -> {
            List<Exam> exams=new ArrayList<>();
            try(var s=c.prepareStatement("SELECT * FROM exams WHERE course_offering_id=? ORDER BY exam_date,id")) {
                s.setLong(1,offeringId);
                try(var r=s.executeQuery()) { while(r.next()) { exams.add(map(c,r)); } }
            }
            return List.copyOf(exams);
        });
    }
    public void delete(long offeringId,long id) {
        jdbc.transaction(c -> { draft(required(c,offeringId,id)); execute(c,"DELETE FROM exams WHERE id=?",id); return null; });
    }
    public Exam finalizeExam(long offeringId,long id,Instant now,LocalDate today) {
        return jdbc.transaction(c -> {
            Exam exam=required(c,offeringId,id); draft(exam);
            if(exam.questions().isEmpty()) { throw new DomainValidationException("Add at least one question before finalizing."); }
            for(ExamQuestion q:exam.questions()) {
                execute(c,"UPDATE questions SET usage_count=usage_count+1,last_used_date=CASE WHEN last_used_date IS NULL OR last_used_date<? THEN ? ELSE last_used_date END WHERE id=?",
                        today,today,q.questionId());
            }
            execute(c,"UPDATE exams SET status='FINALIZED',updated_at=? WHERE id=?",later(now,exam.updatedAt()),id);
            return required(c,offeringId,id);
        });
    }
    public Exam archive(long offeringId,long id,Instant now) {
        return jdbc.transaction(c -> {
            Exam exam=required(c,offeringId,id);
            if(exam.status()!=ExamStatus.FINALIZED) { throw new DomainValidationException("Only a finalized exam can be archived."); }
            execute(c,"UPDATE exams SET status='ARCHIVED',updated_at=? WHERE id=?",later(now,exam.updatedAt()),id);
            return required(c,offeringId,id);
        });
    }
    private static Instant later(Instant a,Instant b) { return a.isBefore(b)?b:a; }
    private static int hundredths(BigDecimal marks) { return marks.movePointRight(2).intValueExact(); }
    private static void draft(Exam exam) {
        if(exam.status()!=ExamStatus.DRAFT) { throw new DomainValidationException("This exam is finalized or archived and cannot be edited or deleted."); }
    }
    private static long course(Connection c,long offeringId) throws SQLException {
        try(var s=c.prepareStatement("SELECT course_id FROM course_offerings WHERE id=?")) {
            s.setLong(1,offeringId);
            try(var r=s.executeQuery()) {
                if(!r.next()) { throw new DomainValidationException("Select an existing offering."); }
                return r.getLong(1);
            }
        }
    }
    private static void execute(Connection c,String sql,Object...values) throws SQLException {
        try(var s=c.prepareStatement(sql)) { JdbcOperations.bind(s,values); s.executeUpdate(); }
    }
    private static Exam required(Connection c,long offeringId,long id) throws SQLException {
        return read(c,offeringId,id).orElseThrow(() -> new DomainValidationException("Exam not found in this offering. Refresh and retry."));
    }
    private static Optional<Exam> read(Connection c,long offeringId,long id) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM exams WHERE id=? AND course_offering_id=?")) {
            JdbcOperations.bind(s,new Object[]{id,offeringId});
            try(var r=s.executeQuery()) { return r.next()?Optional.of(map(c,r)):Optional.empty(); }
        }
    }
    private static Exam map(Connection c,ResultSet r) throws SQLException {
        long id=r.getLong("id"); List<ExamQuestion> questions=new ArrayList<>();
        try(var s=c.prepareStatement("SELECT * FROM exam_questions WHERE exam_id=? ORDER BY position")) {
            s.setLong(1,id);
            try(var q=s.executeQuery()) {
                while(q.next()) {
                    long source=q.getLong("question_id"); List<AnswerChoice> choices=new ArrayList<>();
                    try(var cs=c.prepareStatement("SELECT choice_text,is_correct FROM exam_question_choices WHERE exam_id=? AND question_id=? ORDER BY position")) {
                        JdbcOperations.bind(cs,new Object[]{id,source});
                        try(var cr=cs.executeQuery()) { while(cr.next()) { choices.add(new AnswerChoice(cr.getString(1),cr.getBoolean(2))); } }
                    }
                    questions.add(new ExamQuestion(source,q.getInt("position"),QuestionType.valueOf(q.getString("question_type")),
                            q.getString("question_text"),q.getString("topic"),Difficulty.valueOf(q.getString("difficulty")),
                            BigDecimal.valueOf(q.getLong("marks_hundredths"),2),q.getString("answer"),q.getString("explanation"),q.getString("image_path"),choices));
                }
            }
        }
        return new Exam(id,r.getLong("course_offering_id"),r.getString("title"),LocalDate.parse(r.getString("exam_date")),
                r.getInt("duration_minutes"),BigDecimal.valueOf(r.getLong("intended_marks_hundredths"),2),r.getString("instructions"),
                ExamStatus.valueOf(r.getString("status")),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")),questions);
    }
}
