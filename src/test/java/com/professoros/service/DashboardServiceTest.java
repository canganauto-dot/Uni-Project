package com.professoros.service;

import com.professoros.model.*;
import com.professoros.support.LectureFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {
    @TempDir Path directory;
    LectureFixture f;
    DashboardService service;
    final LocalDate today=LocalDate.of(2026,10,19);
    @BeforeEach void setup() throws Exception {
        f=LectureFixture.create(directory.resolve("dashboard.db"));
        service=new DashboardService(f.academic(),Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC),ZoneOffset.UTC));
    }
    @Test void unselectedContextHasRealGlobalCountsAndNoInventedProgress() {
        f.academic().students().create(new StudentInput("001","Ahmed","Ali","",""));
        var overview=service.load(null);
        assertEquals(1,overview.students()); assertEquals(2,overview.offerings());
        assertNull(overview.activeEnrollments()); assertNull(overview.progress());
        assertTrue(overview.upcoming().isEmpty()); assertEquals(today,overview.asOf());
    }
    @Test void countsOnlyActiveEnrollmentsInSelectedOffering() {
        for(int i=0;i<3;i++) {
            var student=f.academic().students().create(new StudentInput("00"+i,"Student","Name","",""));
            var enrollment=f.academic().enrollments().enroll(student.id(),i==2?f.second().id():f.first().id());
            if(i==1) f.academic().enrollments().updateStatus(enrollment.id(),EnrollmentStatus.WITHDRAWN);
        }
        var overview=service.load(f.first().id());
        assertEquals(3,overview.students()); assertEquals(1,overview.activeEnrollments());
    }
    @Test void upcomingIsChronologicalScopedAndExcludesCompletedCancelledAndPastLectures() {
        var lectures=f.academic().lectures(); long id=f.first().id();
        lectures.create(id,LectureFixture.input(1,today.plusDays(3),LectureStatus.PLANNED));
        lectures.create(id,LectureFixture.input(2,today,LectureStatus.PARTIALLY_COMPLETED));
        lectures.create(id,LectureFixture.input(3,today,LectureStatus.COMPLETED));
        lectures.create(id,LectureFixture.input(4,today,LectureStatus.CANCELLED));
        lectures.create(id,LectureFixture.input(5,today.minusDays(1),LectureStatus.PLANNED));
        lectures.create(f.second().id(),LectureFixture.input(1,today,LectureStatus.PLANNED));
        f.academic().exams().save(id,null,new ExamInput("Draft midterm",today.plusDays(1),60,BigDecimal.TEN,""),List.of());
        var overview=service.load(id);
        assertEquals(List.of("Lecture 2","Draft midterm","Lecture 1"),overview.upcoming().stream().map(DashboardOverview.Activity::title).toList());
        assertTrue(overview.upcoming().get(1).detail().contains("DRAFT"));
        assertEquals(lectures.overview(id).progress(),overview.progress());
        assertEquals(4,overview.recentlyUpdated().size());
    }
    @Test void activityListsAreBoundedAndImmutable() {
        for(int i=1;i<=8;i++) f.academic().lectures().create(f.first().id(),LectureFixture.input(i,today.plusDays(i),LectureStatus.PLANNED));
        var overview=service.load(f.first().id());
        assertEquals(5,overview.upcoming().size()); assertEquals(4,overview.recentlyUpdated().size());
        assertEquals("Lecture 1",overview.upcoming().getFirst().title());
        assertThrows(UnsupportedOperationException.class,()->overview.upcoming().clear());
    }
}
