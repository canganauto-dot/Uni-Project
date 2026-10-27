package com.professoros.service;
import com.professoros.model.*;
import java.time.*;
import java.util.*;

/** Composes existing read services; no new schema, queries or academic business rules. */
public final class DashboardService {
    private final AcademicServices academic;
    private final Clock clock;
    public DashboardService(AcademicServices academic) { this(academic,Clock.systemDefaultZone()); }
    public DashboardService(AcademicServices academic,Clock clock) { this.academic=academic; this.clock=clock; }
    public DashboardOverview load(Long offeringId) {
        int students=academic.students().findAll().size(),offerings=academic.offerings().findAll().size();
        return summarize(students,offerings,
                offeringId==null?List.of():academic.enrollments().findStudentsForOffering(offeringId),
                offeringId==null?null:academic.lectures().overview(offeringId),
                offeringId==null?List.of():academic.exams().findByOffering(offeringId));
    }
    /** Builds the same overview from data already loaded by a workspace refresh. Performs no IO. */
    public DashboardOverview summarize(int students,int offerings,List<EnrolledStudent> roster,
                                       LectureOverview lectures,List<Exam> exams) {
        LocalDate today=LocalDate.now(clock);
        if(lectures==null) { return new DashboardOverview(students,offerings,null,null,List.of(),List.of(),today); }
        int enrollmentCount=(int)roster.stream().filter(s->s.enrollment().status()==EnrollmentStatus.ACTIVE).count();
        List<DashboardOverview.Activity> upcoming=new ArrayList<>(),updated=new ArrayList<>();
        for(var lecture:lectures.lectures()) {
            var activity=new DashboardOverview.Activity("Lecture",lecture.title(),lecture.plannedDate(),"Lecture "+lecture.lectureNumber()+" · "+lecture.status().toString().replace('_',' '),lecture.updatedAt());
            updated.add(activity);
            if((lecture.status()==LectureStatus.PLANNED || lecture.status()==LectureStatus.PARTIALLY_COMPLETED) && !lecture.plannedDate().isBefore(today)) { upcoming.add(activity); }
        }
        for(var exam:exams) {
            var activity=new DashboardOverview.Activity("Exam",exam.title(),exam.date(),exam.status()+" · "+exam.durationMinutes()+" min",exam.updatedAt());
            updated.add(activity);
            if(exam.status()!=ExamStatus.ARCHIVED && !exam.date().isBefore(today)) { upcoming.add(activity); }
        }
        Comparator<DashboardOverview.Activity> chronological=Comparator.comparing(DashboardOverview.Activity::date).thenComparing(DashboardOverview.Activity::kind).thenComparing(DashboardOverview.Activity::title);
        return new DashboardOverview(students,offerings,enrollmentCount,lectures.progress(),upcoming.stream().sorted(chronological).limit(5).toList(),
                updated.stream().sorted(Comparator.comparing(DashboardOverview.Activity::updatedAt).reversed().thenComparing(chronological)).limit(4).toList(),today);
    }
}
