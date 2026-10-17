package com.professoros.service;

import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.support.LectureFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.time.*;
import java.util.stream.Stream;
import static com.professoros.support.LectureFixture.input;
import static org.junit.jupiter.api.Assertions.*;

class LectureServiceTest {
    @TempDir Path directory;
    LectureFixture fixture; LectureService service;
    static final LocalDate TODAY = LocalDate.of(2026,10,17);
    static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
    @BeforeEach void setup() throws Exception {
        fixture = LectureFixture.create(directory.resolve("lectures.db"));
        service = new LectureService(new LectureRepository(fixture.database()),new CourseOfferingRepository(fixture.database()),
                Clock.fixed(NOW,ZoneId.of("Asia/Baghdad")));
    }
    static Stream<LectureInput> invalidInputs() {
        return Stream.of(null,
                new LectureInput(null,"Title",TODAY,"","",60,LectureStatus.PLANNED,""),
                input(0,TODAY,LectureStatus.PLANNED), input(10000,TODAY,LectureStatus.PLANNED),
                new LectureInput(1," ",TODAY,"","",60,LectureStatus.PLANNED,""),
                new LectureInput(1,"x".repeat(201),TODAY,"","",60,LectureStatus.PLANNED,""),
                input(1,null,LectureStatus.PLANNED), input(1,LocalDate.of(10000,1,1),LectureStatus.PLANNED),
                new LectureInput(1,"Title",TODAY,"","",null,LectureStatus.PLANNED,""),
                new LectureInput(1,"Title",TODAY,"","",0,LectureStatus.PLANNED,""),
                new LectureInput(1,"Title",TODAY,"","",1441,LectureStatus.PLANNED,""),
                input(1,TODAY,null),
                new LectureInput(1,"Title",TODAY,"x".repeat(5001),"",60,LectureStatus.PLANNED,""),
                new LectureInput(1,"Title",TODAY,"","x".repeat(10001),60,LectureStatus.PLANNED,""),
                new LectureInput(1,"Title",TODAY,"","",60,LectureStatus.PLANNED,"bad\0note"));
    }
    @ParameterizedTest @MethodSource("invalidInputs") void rejectsInvalidInputWithoutWriting(LectureInput invalid) {
        assertThrows(DomainValidationException.class, () -> service.create(fixture.first().id(),invalid));
        assertTrue(service.overview(fixture.first().id()).lectures().isEmpty());
    }
    @Test void normalizesTextAndPreservesTimestampsAcrossEdits() {
        long offering = fixture.first().id();
        Lecture saved = service.create(offering,new LectureInput(1,"  مقدمة  ",TODAY,null," Details ",60,LectureStatus.PLANNED,null));
        assertEquals("مقدمة",saved.title()); assertEquals("",saved.topics()); assertEquals("Details",saved.description()); assertEquals("",saved.professorNotes());
        assertEquals(NOW,saved.createdAt());
        LectureService later = new LectureService(new LectureRepository(fixture.database()),new CourseOfferingRepository(fixture.database()),
                Clock.fixed(NOW.plusSeconds(30),ZoneOffset.UTC));
        Lecture edited = later.update(offering,saved.id(),input(1,TODAY,LectureStatus.COMPLETED));
        assertEquals(saved.createdAt(),edited.createdAt()); assertEquals(NOW.plusSeconds(30),edited.updatedAt());
        assertEquals(edited.updatedAt(),service.update(offering,saved.id(),input(1,TODAY,LectureStatus.PLANNED)).updatedAt());
    }
    @Test void duplicatesMissingOfferingsAndCrossOfferingChangesAreRejected() {
        long first = fixture.first().id(), second = fixture.second().id();
        Lecture saved = service.create(first,input(1,TODAY,LectureStatus.PLANNED));
        Lecture other = service.create(first,input(2,TODAY,LectureStatus.PLANNED));
        assertThrows(DomainValidationException.class, () -> service.create(first,input(1,TODAY,LectureStatus.PLANNED)));
        assertThrows(DomainValidationException.class, () -> service.update(first,other.id(),input(1,TODAY,LectureStatus.PLANNED)));
        assertDoesNotThrow(() -> service.create(second,input(1,TODAY,LectureStatus.PLANNED)));
        assertThrows(DomainValidationException.class, () -> service.update(second,saved.id(),input(5,TODAY,LectureStatus.PLANNED)));
        assertThrows(DomainValidationException.class, () -> service.delete(second,saved.id()));
        assertThrows(DomainValidationException.class, () -> service.create(999,input(1,TODAY,LectureStatus.PLANNED)));
        assertThrows(DomainValidationException.class, () -> service.overview(999));
    }
    @Test void progressUsesOnlyCompletedAndExcludesCancelledFromDenominator() {
        long id = fixture.first().id();
        service.create(id,input(1,TODAY.minusDays(2),LectureStatus.COMPLETED));
        service.create(id,input(2,TODAY.minusDays(1),LectureStatus.PARTIALLY_COMPLETED));
        service.create(id,input(3,TODAY,LectureStatus.PLANNED));
        service.create(id,input(4,TODAY.minusDays(10),LectureStatus.CANCELLED));
        LectureProgress result = service.overview(id).progress();
        assertEquals(4,result.total()); assertEquals(1,result.completed()); assertEquals(1,result.partial()); assertEquals(1,result.cancelled());
        assertEquals(100.0/3,result.completionPercent(),0.0001); assertEquals(1,result.overdue()); assertEquals(1,result.dueToday());
        assertTrue(result.scheduleStatus().startsWith("Behind schedule")); assertEquals(TODAY,result.asOf());
        assertEquals(0,service.overview(fixture.second().id()).progress().total());
    }
    @Test void emptyCancelledCompleteAndUpcomingStatesAvoidFalseProgress() {
        long id = fixture.first().id();
        assertEquals("No lectures scheduled",service.overview(id).progress().scheduleStatus());
        assertEquals(0,service.overview(id).progress().completionPercent());
        Lecture lecture = service.create(id,input(1,TODAY,LectureStatus.CANCELLED));
        assertEquals("All lectures cancelled",service.overview(id).progress().scheduleStatus());
        assertEquals(0,service.overview(id).progress().completionPercent());
        service.update(id,lecture.id(),input(1,TODAY,LectureStatus.COMPLETED));
        assertEquals(100,service.overview(id).progress().completionPercent());
        assertEquals("All scheduled lectures completed",service.overview(id).progress().scheduleStatus());
        service.update(id,lecture.id(),input(1,TODAY.plusDays(1),LectureStatus.PLANNED));
        assertEquals(TODAY.plusDays(1),service.overview(id).progress().nextPlannedDate());
        assertTrue(service.overview(id).progress().scheduleStatus().startsWith("On schedule"));
        service.update(id,lecture.id(),input(1,TODAY,LectureStatus.PARTIALLY_COMPLETED));
        assertTrue(service.overview(id).progress().scheduleStatus().startsWith("Due today"));
    }
    @Test void lecturesProtectOfferingDeletionAndReassignmentWithoutEnrollment() {
        service.create(fixture.first().id(),input(1,TODAY,LectureStatus.PLANNED));
        assertThrows(DomainValidationException.class, () -> fixture.academic().offerings().delete(fixture.first().id()));
        var term = fixture.academic().terms().create(new AcademicTermInput("Spring","2027",TODAY,TODAY.plusMonths(4),AcademicStatus.PLANNED));
        assertThrows(DomainValidationException.class, () -> fixture.academic().offerings().update(fixture.first().id(),
                new CourseOfferingInput(fixture.course().id(),term.id(),"A","",AcademicStatus.ACTIVE)));
    }
}
