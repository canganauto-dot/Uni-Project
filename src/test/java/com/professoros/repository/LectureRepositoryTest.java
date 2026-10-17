package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.*;
import com.professoros.support.LectureFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import static com.professoros.support.LectureFixture.input;
import static org.junit.jupiter.api.Assertions.*;

class LectureRepositoryTest {
    @TempDir Path directory;
    LectureFixture fixture; LectureRepository repository;
    final Instant now = Instant.parse("2026-10-17T09:00:00Z");
    final LocalDate date = LocalDate.of(2026,10,17);
    @BeforeEach void setup() throws Exception {
        fixture = LectureFixture.create(directory.resolve("lectures.db")); repository = new LectureRepository(fixture.database());
    }
    @Test void createsUpdatesAllFieldsReopensAndDeletesOnlyWithinOffering() throws Exception {
        long offering = fixture.first().id();
        Lecture saved = repository.create(offering, input(1, date, LectureStatus.PLANNED), now);
        var edit = new LectureInput(3, "Changed", date.plusDays(2), "Topics", "Description", 90, LectureStatus.PARTIALLY_COMPLETED, "Notes");
        Lecture updated = repository.update(offering, saved.id(), edit, now.plusSeconds(5)).orElseThrow();
        assertEquals(3, updated.lectureNumber()); assertEquals("Changed", updated.title()); assertEquals(date.plusDays(2), updated.plannedDate());
        assertEquals("Topics", updated.topics()); assertEquals("Description", updated.description()); assertEquals(90, updated.durationMinutes());
        assertEquals(LectureStatus.PARTIALLY_COMPLETED, updated.status()); assertEquals("Notes", updated.professorNotes());
        assertEquals(saved.createdAt(), updated.createdAt()); assertEquals(now.plusSeconds(5), updated.updatedAt());
        Database reopened = new Database(directory.resolve("lectures.db")); reopened.initialize();
        assertEquals(updated, new LectureRepository(reopened).findById(offering, saved.id()).orElseThrow());
        assertTrue(repository.findById(fixture.second().id(), saved.id()).isEmpty());
        assertTrue(repository.update(fixture.second().id(), saved.id(), edit, now.plusSeconds(6)).isEmpty());
        assertFalse(repository.delete(fixture.second().id(), saved.id()));
        assertTrue(repository.delete(offering, saved.id())); assertTrue(repository.findByOffering(offering).isEmpty());
    }
    @Test void databaseUniquenessAppliesOnlyWithinOneOfferingIncludingEdits() {
        long offering = fixture.first().id();
        repository.create(offering, input(1,date,LectureStatus.PLANNED), now);
        Lecture second = repository.create(offering, input(2,date,LectureStatus.PLANNED), now);
        assertThrows(DomainValidationException.class, () -> repository.create(offering,input(1,date,LectureStatus.CANCELLED),now));
        assertThrows(DomainValidationException.class, () -> repository.update(offering,second.id(),input(1,date,LectureStatus.PLANNED),now));
        assertDoesNotThrow(() -> repository.create(fixture.second().id(),input(1,date,LectureStatus.PLANNED),now));
        assertEquals(2, repository.findByOffering(offering).size());
    }
    @Test void timelineSortsByDateThenNumberAndSearchIsLiteralCaseInsensitive() {
        long offering = fixture.first().id();
        var late = repository.create(offering,input(1,date.plusDays(2),LectureStatus.PLANNED),now);
        var earlyTwo = repository.create(offering,input(2,date,LectureStatus.PLANNED),now);
        var earlyOne = repository.create(offering,new LectureInput(3,"ÉTUDE 100%",date.minusDays(1),"Inheritance","Example",30,LectureStatus.COMPLETED,"Private reminder"),now);
        assertEquals(java.util.List.of(earlyOne,earlyTwo,late), repository.findByOffering(offering));
        for (String query : new String[]{"étude","100%","INHERITANCE","private reminder","3"}) {
            assertEquals(java.util.List.of(earlyOne), repository.search(offering,query));
        }
        assertTrue(repository.search(offering,"' OR 1=1 --").isEmpty());
        assertTrue(repository.search(fixture.second().id(),"").isEmpty());
    }
    @Test void foreignKeysAndDatabaseChecksRemainAuthoritative() {
        assertThrows(DomainValidationException.class, () -> repository.create(999,input(1,date,LectureStatus.PLANNED),now));
        assertThrows(StorageException.class, () -> repository.create(fixture.first().id(),input(0,date,LectureStatus.PLANNED),now));
        assertThrows(StorageException.class, () -> repository.create(fixture.first().id(),
                new LectureInput(1,"Title",date,"","",0,LectureStatus.PLANNED,""),now));
        repository.create(fixture.first().id(),input(1,date,LectureStatus.PLANNED),now);
        CourseOfferingRepository offerings = new CourseOfferingRepository(fixture.database());
        assertThrows(DomainValidationException.class, () -> offerings.delete(fixture.first().id()));
        var other = new CourseRepository(fixture.database()).create(new CourseInput("CS202","Other","",3,""),now);
        assertThrows(DomainValidationException.class, () -> offerings.update(fixture.first().id(),
                new CourseOfferingInput(other.id(),fixture.term().id(),"A","",AcademicStatus.ACTIVE),now.plusSeconds(20)));
    }
    @Test void everyStatusRoundTripsUsingStableDatabaseNames() {
        int number = 1;
        for (LectureStatus status : LectureStatus.values()) {
            Lecture saved = repository.create(fixture.first().id(),input(number++,date,status),now);
            assertEquals(status, repository.findById(fixture.first().id(),saved.id()).orElseThrow().status());
        }
    }
}
