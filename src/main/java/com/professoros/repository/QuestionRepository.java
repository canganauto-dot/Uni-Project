package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Questions and ordered choices form one atomic aggregate, including reads. */
public final class QuestionRepository {
    private final JdbcOperations jdbc;
    public QuestionRepository(Database database) {
        jdbc = new JdbcOperations(database, "This question conflicts with an existing record.");
    }
    public Question create(QuestionInput input, Instant now) {
        return jdbc.transaction(connection -> {
            long id;
            try (var statement = connection.prepareStatement("""
                    INSERT INTO questions(course_id, question_type, question_text, topic, difficulty, marks_hundredths,
                    answer, explanation, image_path, correct_choice_position, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """)) {
                bindInput(statement, input);
                statement.setString(11, now.toString()); statement.setString(12, now.toString());
                try (var rows = statement.executeQuery()) { rows.next(); id = rows.getLong(1); }
            }
            writeChoices(connection, id, input.choices());
            return read(connection, id).orElseThrow();
        });
    }
    public Optional<Question> update(long id, QuestionInput input, Instant now) {
        return jdbc.transaction(connection -> {
            if (read(connection, id).isEmpty()) { return Optional.empty(); }
            // Deferred correct-choice FK allows replacing the aggregate without an invalid committed state.
            try (var statement = connection.prepareStatement("DELETE FROM question_choices WHERE question_id=?")) {
                statement.setLong(1, id); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    UPDATE questions SET course_id=?, question_type=?, question_text=?, topic=?, difficulty=?,
                    marks_hundredths=?, answer=?, explanation=?, image_path=?, correct_choice_position=?, updated_at=?
                    WHERE id=?
                    """)) {
                bindInput(statement, input); statement.setString(11, now.toString()); statement.setLong(12, id);
                statement.executeUpdate();
            }
            writeChoices(connection, id, input.choices());
            return read(connection, id);
        });
    }
    public boolean delete(long id) {
        return jdbc.execute("DELETE FROM questions WHERE id=?", id) == 1;
    }
    public Optional<Question> findById(long id) { return jdbc.transaction(connection -> read(connection, id)); }
    public List<Question> findAll() {
        return jdbc.transaction(connection -> {
            List<Question> result = new ArrayList<>();
            try (var statement = connection.prepareStatement("SELECT * FROM questions ORDER BY updated_at DESC, id DESC");
                 var rows = statement.executeQuery()) {
                while (rows.next()) { result.add(map(connection, rows)); }
            }
            return List.copyOf(result);
        });
    }
    public List<Question> search(String query, Long courseId, QuestionType type, Difficulty difficulty) {
        return findAll().stream().filter(q -> q.matches(query)
                && (courseId == null || q.courseId() == courseId)
                && (type == null || q.type() == type)
                && (difficulty == null || q.difficulty() == difficulty)).toList();
    }
    static Optional<Question> read(Connection connection, long id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM questions WHERE id=?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) { return rows.next() ? Optional.of(map(connection, rows)) : Optional.empty(); }
        }
    }
    private static Question map(Connection connection, ResultSet row) throws SQLException {
        long id = row.getLong("id");
        int correct = row.getInt("correct_choice_position");
        List<AnswerChoice> choices = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT position, choice_text FROM question_choices WHERE question_id=? ORDER BY position")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) { choices.add(new AnswerChoice(rows.getString(2), rows.getInt(1) == correct)); }
            }
        }
        String lastUsed = row.getString("last_used_date");
        return new Question(id, row.getLong("course_id"), QuestionType.valueOf(row.getString("question_type")),
                row.getString("question_text"), row.getString("topic"), Difficulty.valueOf(row.getString("difficulty")),
                BigDecimal.valueOf(row.getLong("marks_hundredths"), 2), row.getString("answer"),
                row.getString("explanation"), row.getString("image_path"), choices, row.getLong("usage_count"),
                lastUsed == null ? null : LocalDate.parse(lastUsed), Instant.parse(row.getString("created_at")),
                Instant.parse(row.getString("updated_at")));
    }
    private static void bindInput(PreparedStatement statement, QuestionInput input) throws SQLException {
        Integer correct = null;
        for (int i = 0; i < input.choices().size(); i++) { if (input.choices().get(i).correct()) { correct = i + 1; } }
        JdbcOperations.bind(statement, new Object[]{input.courseId(), input.type().name(), input.text(), input.topic(),
                input.difficulty().name(), input.marks().movePointRight(2).intValueExact(), input.answer(),
                input.explanation(), input.imagePath(), correct});
    }
    private static void writeChoices(Connection connection, long id, List<AnswerChoice> choices) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO question_choices(question_id, position, choice_text) VALUES (?, ?, ?)")) {
            for (int i = 0; i < choices.size(); i++) {
                statement.setLong(1, id); statement.setInt(2, i + 1); statement.setString(3, choices.get(i).text());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
