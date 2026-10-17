package com.professoros.support;
import com.professoros.database.Database;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.service.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

public record QuestionFixture(Database database, Course course, Course second, QuestionRepository repository, QuestionService service) {
    public static QuestionFixture create(Path file) throws Exception {
        Database database = new Database(file); database.initialize();
        CourseService courses = new CourseService(new CourseRepository(database));
        Course course = courses.create(new CourseInput("CS201", "Object Oriented Programming", "", 3, ""));
        Course second = courses.create(new CourseInput("CS202", "Algorithms", "", 3, ""));
        QuestionRepository repository = new QuestionRepository(database);
        return new QuestionFixture(database, course, second, repository, new QuestionService(repository, new CourseRepository(database)));
    }
    public QuestionInput input(QuestionType type) {
        return new QuestionInput(course.id(), type, "Which concept hides implementation?", "Encapsulation",
                Difficulty.MEDIUM, new BigDecimal("2.50"), type == QuestionType.TRUE_FALSE ? "TRUE" : "Model answer",
                "Explain the public interface.", "", type == QuestionType.MCQ
                ? List.of(new AnswerChoice("Encapsulation", true), new AnswerChoice("Inheritance", false)) : List.of());
    }
}
