package com.professoros.service;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.support.QuestionFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class QuestionServiceTest {
    @TempDir Path directory;
    QuestionFixture fixture;
    @BeforeEach void setup() throws Exception { fixture = QuestionFixture.create(directory.resolve("bank.db")); }
    @ParameterizedTest @EnumSource(QuestionType.class)
    void supportsEachTypeAndPreservesCreationTime(QuestionType type) {
        var service = new QuestionService(fixture.repository(), new CourseRepository(fixture.database()),
                Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC));
        Question created = service.create(fixture.input(type));
        assertEquals(type, created.type()); assertEquals(new BigDecimal("2.50"), created.marks());
        var earlier = new QuestionService(fixture.repository(), new CourseRepository(fixture.database()),
                Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC));
        Question updated = earlier.update(created.id(), fixture.input(type));
        assertEquals(created.createdAt(), updated.createdAt()); assertEquals(created.updatedAt(), updated.updatedAt());
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0","-1","1000.01","0.001"})
    void rejectsInvalidMarks(String value) {
        var base = fixture.input(QuestionType.ESSAY);
        var input = new QuestionInput(base.courseId(),base.type(),base.text(),"",base.difficulty(),
                value == null ? null : new BigDecimal(value),"","","",List.of());
        assertThrows(DomainValidationException.class, () -> fixture.service().create(input));
        assertTrue(fixture.service().findAll().isEmpty());
    }
    static Stream<List<AnswerChoice>> invalidChoices() {
        return Stream.of(List.of(), List.of(new AnswerChoice("A",true)),
                List.of(new AnswerChoice("A",false),new AnswerChoice("B",false)),
                List.of(new AnswerChoice("A",true),new AnswerChoice("B",true)),
                List.of(new AnswerChoice("A",true),new AnswerChoice(" a ",false)),
                List.of(new AnswerChoice("",true),new AnswerChoice("B",false)),
                Collections.nCopies(11,new AnswerChoice("A",true)),
                Arrays.asList(null,new AnswerChoice("B",true)));
    }
    @ParameterizedTest @MethodSource("invalidChoices")
    void rejectsInvalidMcqConfiguration(List<AnswerChoice> choices) {
        var base=fixture.input(QuestionType.MCQ);
        assertThrows(DomainValidationException.class, () -> fixture.service().create(new QuestionInput(base.courseId(),base.type(),
                base.text(),"",base.difficulty(),base.marks(),"","","",choices)));
    }
    @Test void requiredFieldsRelationshipsAndTextLimits() {
        var s=fixture.service(); var base=fixture.input(QuestionType.ESSAY);
        assertThrows(DomainValidationException.class, () -> s.create(null));
        for (Long course : Arrays.asList(null,999L)) {
            assertThrows(DomainValidationException.class, () -> s.create(new QuestionInput(course,base.type(),base.text(),"",base.difficulty(),base.marks(),"","","",List.of())));
        }
        assertThrows(DomainValidationException.class, () -> s.create(new QuestionInput(base.courseId(),null,base.text(),"",base.difficulty(),base.marks(),"","","",List.of())));
        assertThrows(DomainValidationException.class, () -> s.create(new QuestionInput(base.courseId(),base.type(),base.text(),"",null,base.marks(),"","","",List.of())));
        for (String text : List.of(" ", "x".repeat(20001), "Bad\0text")) {
            assertThrows(DomainValidationException.class, () -> s.create(new QuestionInput(base.courseId(),base.type(),text,"",base.difficulty(),base.marks(),"","","",List.of())));
        }
        assertThrows(DomainValidationException.class, () -> s.create(new QuestionInput(base.courseId(),base.type(),base.text(),"",base.difficulty(),base.marks(),"","","relative.png",List.of())));
        assertThrows(DomainValidationException.class, () -> s.create(new QuestionInput(base.courseId(),base.type(),base.text(),"",base.difficulty(),base.marks(),"","","",List.of(new AnswerChoice("A",true)))));
        assertThrows(DomainValidationException.class, () -> s.findById(999));
        assertThrows(DomainValidationException.class, () -> s.update(999,base));
        assertThrows(DomainValidationException.class, () -> s.delete(999));
    }
    @Test void normalizesUnicodeAndTrueFalseAndAllowsOptionalModelAnswers() {
        var base=fixture.input(QuestionType.TRUE_FALSE);
        var saved=fixture.service().create(new QuestionInput(base.courseId(),base.type()," سؤال "," Topic ",Difficulty.EASY,
                new BigDecimal("0.0100")," false "," Explain ",null,List.of()));
        assertEquals("سؤال",saved.text()); assertEquals("FALSE",saved.answer()); assertEquals("",saved.imagePath());
        assertEquals(new BigDecimal("0.01"),saved.marks());
        assertThrows(DomainValidationException.class, () -> fixture.service().create(new QuestionInput(base.courseId(),base.type(),base.text(),"",base.difficulty(),base.marks(),"yes","","",List.of())));
        var essay=fixture.service().create(new QuestionInput(base.courseId(),QuestionType.ESSAY,"Discuss inheritance",null,Difficulty.HARD,
                new BigDecimal("1000"),null,null,null,null));
        assertEquals("",essay.answer()); assertTrue(essay.choices().isEmpty());
    }
}
