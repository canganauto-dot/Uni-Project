package com.professoros.demo;

import com.professoros.database.Database;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.service.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Explicit developer tool. Only operates on a verified backup's staging copy. */
public final class DemoDataSeeder {
    private static final List<String> TABLES = List.of("attendance", "student_grades", "exam_question_choices",
        "exam_questions", "exams", "question_choices", "questions", "assessments", "assessment_categories",
        "grading_scales", "lectures", "enrollments", "course_offerings", "students", "academic_terms", "courses");
    private final Database database;
    private final AcademicServices a;
    private final LocalDate today;
    private DemoDataSeeder(Path path, LocalDate today) {
        this.database = new Database(path); this.a = AcademicServices.create(database); this.today = today;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: DemoDataSeeder <.demo-stage-*.db> <yyyy-MM-dd> <new-sample-directory>");
        var date=LocalDate.parse(args[1]);
        seed(Path.of(args[0]), date);
        DemoImportSamples.write(Path.of(args[2]),date.getYear());
        System.out.println("Demo staging database validated. Use demo_workspace.py install to replace the original.");
    }
    public static void seed(Path stage, LocalDate asOf) throws Exception {
        if (!Files.isRegularFile(stage) || !stage.getFileName().toString().startsWith(".demo-stage-"))
            throw new IllegalArgumentException("Refusing to seed a live database. Prepare a verified .demo-stage- copy first.");
        var seeder = new DemoDataSeeder(stage, asOf);
        seeder.database.initialize(); // Normal immutable-migration/checksum verification.
        seeder.clearStage();
        seeder.populate();
        seeder.verify();
        Files.writeString(Path.of(stage+".ready.sha256"),HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(stage))));
    }
    private void clearStage() throws Exception {
        try (var c = database.openConnection()) {
            var known = new HashSet<>(TABLES); known.add("schema_version");
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                while (r.next()) if (!known.contains(r.getString(1))) throw new IllegalStateException("Unknown table; review demo tool before replacing data: " + r.getString(1));
            }
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                // Fixed, dependency-ordered identifiers; never user-provided SQL.
                for (String table : TABLES) s.executeUpdate("DELETE FROM " + table);
                c.commit();
            } catch (Exception failure) { c.rollback(); throw failure; }
        }
    }
    private void populate() {
        var current = a.terms().create(new AcademicTermInput("Fall", today.getYear()+"–"+(today.getYear()+1), today.minusWeeks(6), today.plusWeeks(10), AcademicStatus.ACTIVE));
        var previous = a.terms().create(new AcademicTermInput("Spring", (today.getYear()-1)+"–"+today.getYear(), today.minusMonths(8), today.minusMonths(4), AcademicStatus.ACTIVE));
        var next = a.terms().create(new AcademicTermInput("Spring", today.getYear()+"–"+(today.getYear()+1), today.plusMonths(4), today.plusMonths(8), AcademicStatus.PLANNED));
        var students = students();
        var courses = new CourseService(new CourseRepository(database));
        for (int index=0; index<DemoCurriculum.SUBJECTS.size(); index++) {
            var subject = DemoCurriculum.SUBJECTS.get(index);
            var course = courses.create(new CourseInput(subject.code(), subject.name(), "Computer Science & Software Engineering", 3,
                "Fictional Professor OS demonstration curriculum. Lectures, assessments and students are synthetic. Practical work covers " + subject.topics().stream().map(DemoCurriculum.Topic::name).toList()));
            var bank = questions(course.id(), subject);
            offering(course, subject, bank, current, students, index, "A", 0);
            if (index<2) offering(course, subject, bank, current, students, index+3, "B", 0);
            if (index<2) offering(course, subject, bank, previous, students, index, "A", -1);
            if (index<2) offering(course, subject, bank, next, students, index, "A", 1);
        }
        a.terms().update(previous.id(), new AcademicTermInput(previous.name(), previous.academicYear(),previous.startDate(),previous.endDate(),AcademicStatus.COMPLETED));
    }
    private List<Student> students() {
        String[] arabicFirst = {"أحمد","سارة","علي","نور","عمر","مريم","حسن","زينب","يوسف","فاطمة","مصطفى","هبة","خالد","رنا","حيدر","دينا","كريم","لينا"};
        String[] arabicLast = {"العبيدي","الهاشمي","الجبوري","السامرائي","النعيمي"};
        String[] englishFirst = {"Adam","Emily","Daniel","Sofia","James","Olivia","Noah","Amelia","Ethan","Maya","Lucas","Chloe","Liam","Grace","Oliver","Hannah","Leo","Aisha"};
        String[] englishLast = {"Wilson","Bennett","Parker","Morgan","Reed"};
        var result = new ArrayList<Student>();
        for (int i=0;i<180;i++) {
            int n=i%90;
            result.add(a.students().create(new StudentInput(String.format(Locale.ROOT,"00%d%03d",today.getYear(),i+1),
                (i<90?arabicFirst:englishFirst)[n%18], (i<90?arabicLast:englishLast)[n/18],
                i%13==0?"":"student"+(i+1)+"@example.edu", "Fictional demo student — not a real university record.")));
        }
        return result;
    }
    private List<Question> questions(long course, DemoCurriculum.Subject subject) {
        var result = new ArrayList<Question>();
        for (int t=0;t<subject.topics().size();t++) {
            var topic=subject.topics().get(t);
            for (var type:QuestionType.values()) {
                String text=switch(type) {
                    case MCQ -> "Which statement best describes "+topic.name().toLowerCase(Locale.ROOT)+"?";
                    case TRUE_FALSE -> (t%2==0?topic.principle():"A correct implementation of "+topic.name().toLowerCase(Locale.ROOT)+" never needs boundary tests")+". True or false?";
                    case SHORT_ANSWER -> "Explain "+topic.name().toLowerCase(Locale.ROOT)+" and give one example in a university registration system.";
                    case ESSAY -> "Evaluate a design that uses "+topic.name().toLowerCase(Locale.ROOT)+". Discuss correctness, maintenance and one trade-off. وضّح إجابتك بمثال.";
                    case CODING -> "Write a small program to "+topic.exercise()+". Include boundary cases and explain the result.";
                };
                var choices=type==QuestionType.MCQ?new ArrayList<>(List.of(new AnswerChoice(topic.principle(),true),
                    new AnswerChoice("Input validation can always be omitted when the user interface is trusted",false),
                    new AnswerChoice("Correct behavior never needs an explicit contract or test",false),
                    new AnswerChoice("Duplicating all data removes the need to check consistency",false))):new ArrayList<AnswerChoice>();
                if(type==QuestionType.MCQ)Collections.rotate(choices,t);
                result.add(a.questions().create(new QuestionInput(course,type,text,topic.name(),Difficulty.values()[(t+type.ordinal())%3],
                    BigDecimal.valueOf(type==QuestionType.MCQ||type==QuestionType.TRUE_FALSE?2:type==QuestionType.SHORT_ANSWER?5:10),
                    type==QuestionType.TRUE_FALSE?(t%2==0?"TRUE":"FALSE"):topic.principle()+". Award credit for a correct example, explicit assumptions and boundary tests.",
                    "Assess the principle, then the worked example. "+topic.principle()+".","",choices)));
            }
        }
        return result;
    }
    private void offering(Course course, DemoCurriculum.Subject subject, List<Question> bank, AcademicTerm term,
                          List<Student> students, int group, String section, int period) {
        var status=period>0?AcademicStatus.PLANNED:AcademicStatus.ACTIVE;
        var offering=a.offerings().create(new CourseOfferingInput(course.id(),term.id(),section,
            "DEMO · Dr. "+(group%2==0?"Layla Hassan":"Omar Bennett")+" · Weekly 09:00–10:30; see lecture dates · Computing Lab "+(201+group),status));
        // Backdate enrollment through the existing injectable service clock, not raw record updates.
        var enrollments=new EnrollmentService(new EnrollmentRepository(database),new StudentRepository(database),
            new CourseOfferingRepository(database),new AcademicTermRepository(database),Clock.fixed(term.startDate().minusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC));
        var roster=new ArrayList<Enrollment>();
        for(int n=0;n<45;n++) roster.add(enrollments.enroll(students.get((group*30+n)%students.size()).id(),offering.id()));
        LocalDate start=period==0?today.minusDays(35):term.startDate().plusDays(7);
        var lectures=new ArrayList<Lecture>();
        for(int n=0;n<12;n++) {
            var topic=subject.topics().get(n/3);
            LocalDate date=start.plusDays(n*7L);
            LectureStatus lectureStatus=date.isBefore(today)?(n==4&&period==0?LectureStatus.PARTIALLY_COMPLETED:LectureStatus.COMPLETED):LectureStatus.PLANNED;
            if(n==8)lectureStatus=LectureStatus.CANCELLED;
            lectures.add(a.lectures().create(offering.id(),new LectureInput(n+1,topic.name()+" · "+List.of("Concepts and examples","Guided laboratory","Review and application").get(n%3),date,
                topic.name(),topic.principle(),90,lectureStatus,lectureStatus==LectureStatus.CANCELLED?"Campus event; independent reading assigned.":"Discuss examples, invite questions, then complete the practice exercise.")));
        }
        for(int l=0;l<lectures.size();l++) {
            var lecture=lectures.get(l);
            if(!lecture.plannedDate().isBefore(today)||lecture.status()==LectureStatus.CANCELLED)continue;
            var marks=new LinkedHashMap<Long,AttendanceStatus>();
            for(int r=0;r<roster.size();r++) {
                int code=(r+l*3)%16;
                if(code==3)continue; // Unmarked is absence of a row, never ABSENT.
                marks.put(roster.get(r).id(),switch(code){case 0->AttendanceStatus.ABSENT;case 1->AttendanceStatus.LATE;case 2->AttendanceStatus.EXCUSED;default->AttendanceStatus.PRESENT;});
            }
            a.attendance().save(offering.id(),lecture.id(),marks);
        }
        assessments(offering.id(),roster,term,period);
        var selection=bank.subList(0,5);
        var total=selection.stream().map(Question::marks).reduce(BigDecimal.ZERO,BigDecimal::add);
        LocalDate midterm=period==0?today.minusDays(7):term.startDate().plusWeeks(6);
        var exam=a.exams().save(offering.id(),null,new ExamInput("Midterm · "+course.courseCode(),midterm,75,total,
            "Answer all questions. Show your reasoning. Dictionaries are allowed; electronic communication is not. اكتب إجابات واضحة."),selection.stream().map(Question::id).toList());
        if(period<=0) {a.exams().finalizeExam(offering.id(),exam.id());if(period<0)a.exams().archive(offering.id(),exam.id());}
        var finalSelection=bank.subList(5,15);
        a.exams().save(offering.id(),null,new ExamInput("Final examination · "+course.courseCode(),period==0?today.plusWeeks(7):term.endDate().minusDays(7),120,
            finalSelection.stream().map(Question::marks).reduce(BigDecimal.ZERO,BigDecimal::add),"Read the paper before starting. Explain design decisions and include tests."),finalSelection.stream().map(Question::id).toList());
        if(period<0) {
            // Final papers in a completed term must not remain drafts.
            var finalExam=a.exams().findByOffering(offering.id()).stream().filter(e->e.status()==ExamStatus.DRAFT).findFirst().orElseThrow();
            a.exams().finalizeExam(offering.id(),finalExam.id());a.exams().archive(offering.id(),finalExam.id());
        }
        if(period<=0) for(int n=0;n<roster.size();n++) {
            if(n%23==22)a.enrollments().updateStatus(roster.get(n).id(),EnrollmentStatus.WITHDRAWN);
            else if(period<0)a.enrollments().updateStatus(roster.get(n).id(),EnrollmentStatus.COMPLETED);
        }
        if(period<0)a.offerings().update(offering.id(),new CourseOfferingInput(course.id(),term.id(),section,offering.notes(),AcademicStatus.COMPLETED));
    }
    private void assessments(long offering,List<Enrollment> roster,AcademicTerm term,int period) {
        String[] names={"Assignments","Quizzes","Midterm","Final"};int[] weights={20,10,30,40};
        a.grades().saveScale(offering,GradingScale.standard());
        var changes=new LinkedHashMap<GradeKey,String>();
        for(int k=0;k<4;k++) {
            var category=a.grades().saveCategory(offering,null,names[k],BigDecimal.valueOf(weights[k]));
            LocalDate date=period==0?(k==3?today.plusWeeks(7):today.minusDays(21-k*7L)):term.startDate().plusWeeks(3+k*3L);
            var assessment=a.grades().saveAssessment(offering,null,category.id(),names[k]+" · "+(k<2?"practical work":"written assessment"),BigDecimal.valueOf(weights[k]),date);
            if(date.isAfter(today))continue;
            for(int n=0;n<roster.size();n++) {
                if((period==0&&n%11==0)||(period<0&&n==44&&k==3))continue;
                int percent=40+Math.floorMod(n*7+k*3,59);
                BigDecimal score=n==1&&k==0?BigDecimal.ZERO:BigDecimal.valueOf(weights[k]*percent).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
                changes.put(new GradeKey(roster.get(n).id(),assessment.id()),score.toPlainString());
            }
        }
        a.grades().saveGrades(offering,changes);
    }
    private void verify() throws Exception {
        try(var c=database.openConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!"ok".equals(r.getString(1)))throw new IllegalStateException("Integrity failed");}
            try(var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new IllegalStateException("Foreign keys failed");}
        }
        if(a.students().findAll().size()!=180||a.offerings().findAll().size()!=12)throw new IllegalStateException("Demo counts failed");
        for(var offering:a.offerings().findAll()) {
            var book=a.grades().load(offering.id());
            if(GradeCalculator.weightTotal(book).compareTo(BigDecimal.valueOf(100))!=0)throw new IllegalStateException("Weights failed");
            GradeCalculator.classStatistics(book,GradeCalculator.scores(book));
            AttendanceCalculator.course(a.attendance().load(offering.id()));
            for(var exam:a.exams().findByOffering(offering.id()))if(exam.marksDiffer())throw new IllegalStateException("Exam totals failed");
        }
    }
}
