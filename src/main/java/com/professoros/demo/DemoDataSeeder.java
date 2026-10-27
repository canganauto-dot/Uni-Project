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
            boolean populated;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT EXISTS(SELECT 1 FROM academic_terms UNION ALL SELECT 1 FROM courses UNION ALL SELECT 1 FROM students)")) { populated=r.next()&&r.getBoolean(1); }
            if(populated) {
                try(var s=c.createStatement();var r=s.executeQuery("""
                        SELECT (SELECT count(*) FROM courses WHERE description IS NULL OR description NOT LIKE 'Fictional Professor OS demonstration curriculum.%')
                             + (SELECT count(*) FROM students WHERE notes IS NULL OR notes NOT LIKE 'Fictional demo student — not a real university record.%')
                             + (SELECT count(*) FROM course_offerings WHERE notes IS NULL OR notes NOT LIKE 'DEMO%'),
                               (SELECT count(*) FROM courses), (SELECT count(*) FROM students)
                        """)) {
                    if(!r.next()||r.getInt(1)!=0||r.getInt(2)==0||r.getInt(3)==0)throw new IllegalStateException("Refusing to clear non-demo academic records");
                }
            }
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                // Fixed, dependency-ordered identifiers; never user-provided SQL.
                for (String table : TABLES) s.executeUpdate("DELETE FROM " + table);
                c.commit();
            } catch (Exception failure) { c.rollback(); throw failure; }
        }
    }
    private static final int[] PERCENTAGES = {96,93,91,90,88,86,84,83,81,79,77,75,74,72,70,69,67,66,64,63,61,60,59,57,55,53,51,50,49,47,44,41,37,32,25};
    private static final int[][] SCORE_OFFSETS = {{1,-2,0,0},{3,0,-2,0},{0,4,0,-1},{2,2,2,-3}};
    private void populate() {
        var term = a.terms().create(new AcademicTermInput("Fall", today.getYear()+"–"+(today.getYear()+1),
            today.minusWeeks(8), today.plusWeeks(2), AcademicStatus.ACTIVE));
        var students = students();
        var courses = new CourseService(new CourseRepository(database));
        for (int index=0; index<DemoCurriculum.SUBJECTS.size(); index++) {
            var subject = DemoCurriculum.SUBJECTS.get(index);
            var course = courses.create(new CourseInput(subject.code(), subject.name(), "Computer Science", 3,
                "Fictional Professor OS demonstration curriculum. compact-v2. All academic records are synthetic."));
            offering(course, subject, questions(course.id(),subject), term, students.subList(index*35,(index+1)*35), index);
        }
    }
    private List<Student> students() {
        var result = new ArrayList<Student>();
        for (int i=0;i<DemoStudents.PEOPLE.size();i++) {
            var name = DemoStudents.PEOPLE.get(i);
            result.add(a.students().create(new StudentInput(String.format(Locale.ROOT,"00%d%03d",today.getYear(),i+1),
                name.given(),name.family(),"student"+(i+1)+"@example.edu",
                "Fictional demo student — not a real university record. compact-v2.")));
        }
        return result;
    }
    private List<Question> questions(long course, DemoCurriculum.Subject subject) {
        var result = new ArrayList<Question>();
        for (int t=0;t<subject.topics().size();t++) {
            var topic=subject.topics().get(t);
            for (var type:QuestionType.values()) {
                String text=switch(type) {
                    case MCQ -> "Which statement correctly describes "+topic.name().toLowerCase(Locale.ROOT)+"?";
                    case TRUE_FALSE -> (t%2==0?topic.principle():topic.misconception())+". True or false? Justify your answer.";
                    case SHORT_ANSWER -> "Explain "+topic.name().toLowerCase(Locale.ROOT)+" using the following task: "+topic.exercise()+". State one important boundary case.";
                    case ESSAY -> "Evaluate a design for this task: "+topic.exercise()+". Explain how "+topic.name().toLowerCase(Locale.ROOT)+" affects correctness, compare one alternative, and discuss a maintenance or complexity trade-off.";
                    case CODING -> "Write Java code to "+topic.exercise()+". Include tests for normal and boundary cases.";
                };
                var choices=new ArrayList<AnswerChoice>();
                if(type==QuestionType.MCQ) {
                    choices.add(new AnswerChoice(topic.principle(),true));
                    topic.distractors().forEach(value->choices.add(new AnswerChoice(value,false)));
                    Collections.rotate(choices,t);
                }
                int marks=switch(type) {case MCQ,TRUE_FALSE->2;case SHORT_ANSWER->6;case ESSAY->10;case CODING->5;};
                result.add(a.questions().create(new QuestionInput(course,type,text,topic.name(),Difficulty.values()[(t+type.ordinal())%3],
                    BigDecimal.valueOf(marks),type==QuestionType.TRUE_FALSE?(t%2==0?"TRUE":"FALSE"):topic.solution(),
                    topic.principle()+". "+topic.solution(),"",choices)));
            }
        }
        return result;
    }
    private void offering(Course course, DemoCurriculum.Subject subject, List<Question> bank, AcademicTerm term,
                          List<Student> students, int group) {
        var offering=a.offerings().create(new CourseOfferingInput(course.id(),term.id(),"A",
            "DEMO compact-v2 · "+(group==0?"Dr. Layla Hassan":"Dr. Omar Khalid")+" · Mon/Thu laboratory and tutorial · Room "+(201+group),AcademicStatus.ACTIVE));
        var enrollments=new EnrollmentService(new EnrollmentRepository(database),new StudentRepository(database),
            new CourseOfferingRepository(database),new AcademicTermRepository(database),
            Clock.fixed(term.startDate().atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC));
        var roster=students.stream().map(student->enrollments.enroll(student.id(),offering.id())).toList();
        var start=today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(6);
        for(int n=0;n<12;n++) {
            var topic=subject.topics().get(n/3);
            var date=start.plusWeeks(n/2).plusDays(n%2==0?0:3);
            var lecture=a.lectures().create(offering.id(),new LectureInput(n+1,
                topic.name()+" · "+List.of("Concepts and worked examples","Guided laboratory","Practice and review").get(n%3),
                date,topic.name(),topic.principle(),90,LectureStatus.COMPLETED,topic.solution()));
            var marks=new LinkedHashMap<Long,AttendanceStatus>();
            for(int r=0;r<roster.size();r++) {
                int percent=percentage(r,group), code=Math.floorMod(r*17+n*13+group*7,100);
                int absent=percent>=85?2:percent>=65?8:percent>=50?14:25;
                int late=percent>=85?8:12,excused=percent>=85?2:percent>=65?5:percent>=50?6:8;
                marks.put(roster.get(r).id(),code<absent?AttendanceStatus.ABSENT:code<absent+late?AttendanceStatus.LATE:
                    code<absent+late+excused?AttendanceStatus.EXCUSED:AttendanceStatus.PRESENT);
            }
            a.attendance().save(offering.id(),lecture.id(),marks);
        }
        assessments(offering.id(),roster,group,start);
        exam(offering.id(),course.courseCode(),"Midterm",start.plusWeeks(3),bank.subList(0,10),90,true);
        exam(offering.id(),course.courseCode(),"Final examination",start.plusWeeks(5).plusDays(4),bank,180,true);
        var resit=new ArrayList<>(bank.subList(0,5));resit.addAll(bank.subList(10,15));
        exam(offering.id(),course.courseCode(),"Resit practice paper",today.plusDays(7),resit,90,false);
    }
    private static int percentage(int row,int group) { return PERCENTAGES[(row+group*7)%PERCENTAGES.length]; }
    private void exam(long offering,String code,String title,LocalDate date,List<Question> selection,int minutes,boolean finalize) {
        var marks=selection.stream().map(Question::marks).reduce(BigDecimal.ZERO,BigDecimal::add);
        var saved=a.exams().save(offering,null,new ExamInput(title+" · "+code,date,minutes,marks,
            "Answer all questions. Show intermediate steps. For code, explain assumptions and test boundary cases."),selection.stream().map(Question::id).toList());
        if(finalize)a.exams().finalizeExam(offering,saved.id());
    }
    private void assessments(long offering,List<Enrollment> roster,int group,LocalDate start) {
        String[] categories={"Assignments","Quizzes","Midterm","Final"};int[] weights={20,10,30,40};
        String[] titles={"Laboratory portfolio","Concepts and tracing quiz","Midterm written assessment","Final written and practical assessment"};
        // Preserve the verified demo settings: CS201 passes at 50, CS202 at 60. Calculation/default rules are unchanged.
        var standard=GradingScale.standard();
        a.grades().saveScale(offering,new GradingScale(standard.a(),standard.b(),standard.c(),standard.d(),BigDecimal.valueOf(group==0?50:60)));
        var changes=new LinkedHashMap<GradeKey,String>();
        for(int k=0;k<4;k++) {
            var category=a.grades().saveCategory(offering,null,categories[k],BigDecimal.valueOf(weights[k]));
            var assessment=a.grades().saveAssessment(offering,null,category.id(),titles[k],BigDecimal.valueOf(100),start.plusDays(new int[]{14,18,21,39}[k]));
            for(int n=0;n<roster.size();n++) {
                // Each offset vector has weighted sum zero. Individual assessment scores vary while final boundaries stay exact.
                int score=percentage(n,group)+SCORE_OFFSETS[(n+group)%SCORE_OFFSETS.length][k];
                changes.put(new GradeKey(roster.get(n).id(),assessment.id()),Integer.toString(score));
            }
        }
        a.grades().saveGrades(offering,changes);
    }
    private void verify() throws Exception {
        var expected=Map.ofEntries(Map.entry("courses",2),Map.entry("academic_terms",1),Map.entry("course_offerings",2),
            Map.entry("students",70),Map.entry("enrollments",70),Map.entry("lectures",24),Map.entry("questions",40),
            Map.entry("question_choices",32),Map.entry("exams",6),Map.entry("exam_questions",80),Map.entry("exam_question_choices",64),
            Map.entry("assessment_categories",8),Map.entry("assessments",8),Map.entry("student_grades",280),
            Map.entry("grading_scales",2),Map.entry("attendance",840));
        try(var c=database.openConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!"ok".equals(r.getString(1)))throw new IllegalStateException("Integrity failed");}
            try(var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new IllegalStateException("Foreign keys failed");}
            for(var entry:expected.entrySet()) {
                try(var r=s.executeQuery("SELECT count(*) FROM "+entry.getKey())) {
                    if(!r.next()||r.getInt(1)!=entry.getValue())throw new IllegalStateException("Demo count failed: "+entry.getKey());
                }
            }
        }
        var names=new HashSet<String>();var thirds=new HashSet<String>();var enrolled=new HashSet<Long>();
        for(var student:a.students().findAll()) {
            var parts=student.fullName().split("\\s+");
            if(parts.length!=3||!names.add(student.fullName())||!thirds.add(parts[2]))throw new IllegalStateException("Student name validation failed");
        }
        if(a.terms().findAll().getFirst().status()!=AcademicStatus.ACTIVE)throw new IllegalStateException("Term must be active");
        for(var offering:a.offerings().findAll()) {
            if(offering.status()!=AcademicStatus.ACTIVE)throw new IllegalStateException("Offering must be active");
            if(a.questions().search("",offering.courseId(),null,null).size()!=20)throw new IllegalStateException("Question count failed");
            var book=a.grades().load(offering.id());
            if(book.students().size()!=35||book.assessments().size()!=4||book.grades().size()!=140||GradeCalculator.weightTotal(book).compareTo(BigDecimal.valueOf(100))!=0)
                throw new IllegalStateException("Gradebook coverage failed");
            var percentages=new HashSet<Integer>();
            for(var student:book.students()) {
                if(!enrolled.add(student.student().id()))throw new IllegalStateException("Student enrolled in both courses");
                var result=GradeCalculator.calculate(book,student.enrollment().id(),GradeCalculator.scores(book));
                if(!result.complete())throw new IllegalStateException("Incomplete grade result");
                percentages.add(result.weightedPoints().intValueExact());
            }
            if(!percentages.containsAll(List.of(49,50,51,59,60,61,90,96,25)))throw new IllegalStateException("Grade distribution failed");
            var attendance=a.attendance().load(offering.id());
            if(attendance.lectures().size()!=12||attendance.records().size()!=420||AttendanceCalculator.course(attendance).unmarked()!=0)
                throw new IllegalStateException("Attendance coverage failed");
            var exams=a.exams().findByOffering(offering.id());
            if(exams.size()!=3||exams.stream().filter(e->e.status()==ExamStatus.FINALIZED).count()!=2)throw new IllegalStateException("Exam count/status failed");
            for(var exam:exams) {
                if(exam.marksDiffer()||exam.questions().isEmpty())throw new IllegalStateException("Exam totals failed");
                for(var q:exam.questions())if(a.questions().findById(q.questionId()).courseId()!=offering.courseId())throw new IllegalStateException("Exam course mismatch");
            }
        }
    }
}
