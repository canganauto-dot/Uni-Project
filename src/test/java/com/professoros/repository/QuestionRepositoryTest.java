package com.professoros.repository;
import com.professoros.model.*;
import com.professoros.service.CourseService;
import com.professoros.support.QuestionFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestionRepositoryTest {
    @TempDir Path directory;
    QuestionFixture fixture;
    @BeforeEach void setup() throws Exception { fixture = QuestionFixture.create(directory.resolve("bank.db")); }
    @Test void persistsFullAggregateUpdatesChoicesAndDeletesWithoutOrphans() throws Exception {
        var saved = fixture.repository().create(fixture.input(QuestionType.MCQ), Instant.parse("2026-10-17T00:00:00Z"));
        assertEquals(saved, new QuestionRepository(fixture.database()).findById(saved.id()).orElseThrow());
        assertEquals(0, saved.usageCount()); assertNull(saved.lastUsedDate());
        var input = fixture.input(QuestionType.MCQ);
        var edited = new QuestionInput(fixture.second().id(), input.type(), "Revised", "OOP", Difficulty.HARD,
                input.marks(), "", "Updated explanation", directory.resolve("image.png").toString(),
                List.of(new AnswerChoice("A", false), new AnswerChoice("B", true), new AnswerChoice("C", false)));
        var updated = fixture.repository().update(saved.id(), edited, saved.updatedAt().plusSeconds(10)).orElseThrow();
        assertEquals(edited.choices(), updated.choices()); assertEquals(saved.createdAt(), updated.createdAt());
        assertEquals("Revised", updated.text()); assertEquals(edited.imagePath(), updated.imagePath());
        assertEquals(fixture.second().id(), updated.courseId());
        assertTrue(fixture.repository().delete(saved.id())); assertTrue(fixture.repository().findById(saved.id()).isEmpty());
        assertFalse(fixture.repository().delete(saved.id()));
        try (var c = fixture.database().openConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM question_choices")) {
            assertTrue(r.next()); assertEquals(0, r.getInt(1));
        }
    }
    @Test void failedChoiceWriteRollsBackEntireUpdateAndCreate() {
        var saved = fixture.service().create(fixture.input(QuestionType.MCQ));
        var input = fixture.input(QuestionType.MCQ);
        var invalid = new QuestionInput(input.courseId(), input.type(), "Should roll back", "", input.difficulty(), input.marks(), "", "", "",
                List.of(new AnswerChoice("Valid", true), new AnswerChoice("", false)));
        assertThrows(StorageException.class, () -> fixture.repository().update(saved.id(), invalid, saved.updatedAt().plusSeconds(1)));
        assertEquals(saved, fixture.repository().findById(saved.id()).orElseThrow());
        assertThrows(StorageException.class, () -> fixture.repository().create(invalid, saved.createdAt()));
        assertEquals(1, fixture.repository().findAll().size());
    }
    @Test void courseForeignKeyAndCorrectChoiceForeignKeyAreAuthoritative() throws Exception {
        var saved = fixture.service().create(fixture.input(QuestionType.MCQ));
        assertThrows(DomainValidationException.class, () -> new CourseService(new CourseRepository(fixture.database())).delete(fixture.course().id()));
        try (var c = fixture.database().openConnection(); var s = c.createStatement()) {
            assertThrows(SQLException.class, () -> s.executeUpdate("UPDATE questions SET correct_choice_position=9 WHERE id=" + saved.id()));
            assertThrows(SQLException.class, () -> s.executeUpdate("UPDATE questions SET course_id=99999 WHERE id=" + saved.id()));
            assertThrows(SQLException.class, () -> s.executeUpdate("UPDATE questions SET marks_hundredths=0 WHERE id=" + saved.id()));
        }
        assertEquals(saved, fixture.service().findById(saved.id()));
    }
    @Test void typeChangesRemoveChoicesAndPreserveUsageMetadata() throws Exception {
        var saved = fixture.service().create(fixture.input(QuestionType.MCQ));
        try (var c = fixture.database().openConnection(); var s = c.prepareStatement("UPDATE questions SET usage_count=3,last_used_date='2026-10-16' WHERE id=?")) {
            s.setLong(1,saved.id()); s.executeUpdate();
        }
        var essay = fixture.service().update(saved.id(), fixture.input(QuestionType.ESSAY));
        assertTrue(essay.choices().isEmpty()); assertEquals(3, essay.usageCount());
        assertEquals("2026-10-16", essay.lastUsedDate().toString());
        var mcq = fixture.service().update(saved.id(), fixture.input(QuestionType.MCQ));
        assertEquals(2, mcq.choices().size()); assertEquals(3, mcq.usageCount());
    }
    @Test void searchAndCombinedFiltersMatchLiteralContent() {
        for (QuestionType type : QuestionType.values()) { fixture.service().create(fixture.input(type)); }
        assertEquals(5, fixture.repository().search("ENCAPSULATION", null, null, null).size());
        assertEquals(1, fixture.repository().search("inheritance", fixture.course().id(), QuestionType.MCQ, Difficulty.MEDIUM).size());
        assertTrue(fixture.repository().search("", fixture.second().id(), null, null).isEmpty());
        assertTrue(fixture.repository().search("", null, null, Difficulty.HARD).isEmpty());
        assertTrue(fixture.repository().search("%", null, null, null).isEmpty());
    }
    @Test void batchListsMatchIndependentReadsIncludingChoiceOrderAndUsage() throws Exception {
        assertTrue(fixture.repository().findAll().isEmpty());
        for(QuestionType type:QuestionType.values()) { fixture.service().create(fixture.input(type)); }
        var input=fixture.input(QuestionType.MCQ);
        var special=fixture.service().create(new QuestionInput(fixture.second().id(),QuestionType.MCQ,"Separate course","Topic",Difficulty.HARD,
                input.marks(),"","Explanation","",List.of(new AnswerChoice("Third",false),new AnswerChoice("First",true),new AnswerChoice("Second",false))));
        try(var c=fixture.database().openConnection();var s=c.prepareStatement("UPDATE questions SET usage_count=7,last_used_date='2026-10-20' WHERE id=?")) {
            s.setLong(1,special.id());s.executeUpdate();
        }
        var all=fixture.repository().findAll();
        assertEquals(6,all.size());
        for(var question:all) { assertEquals(fixture.repository().findById(question.id()).orElseThrow(),question); }
        var selected=fixture.repository().search("Separate",fixture.second().id(),QuestionType.MCQ,Difficulty.HARD);
        assertEquals(List.of(fixture.repository().findById(special.id()).orElseThrow()),selected);
        assertEquals(List.of("Third","First","Second"),selected.getFirst().choices().stream().map(AnswerChoice::text).toList());
        assertTrue(selected.getFirst().choices().get(1).correct());
        assertEquals(7,selected.getFirst().usageCount());
        assertEquals("2026-10-20",selected.getFirst().lastUsedDate().toString());
        assertEquals(all.stream().sorted(Comparator.comparing(Question::updatedAt).reversed().thenComparing(Comparator.comparingLong(Question::id).reversed())).toList(),all);
        try(var c=fixture.database().openConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("PRAGMA integrity_check")) { assertTrue(r.next());assertEquals("ok",r.getString(1)); }
            try(var r=s.executeQuery("PRAGMA foreign_key_check")) { assertFalse(r.next()); }
            try(var r=s.executeQuery("SELECT count(*) FROM schema_version")) { assertTrue(r.next());assertEquals(10,r.getInt(1)); }
        }
    }
}
