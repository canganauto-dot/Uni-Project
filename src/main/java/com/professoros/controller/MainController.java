package com.professoros.controller;

import com.professoros.model.OfferingSummary;
import com.professoros.service.*;
import com.professoros.view.*;
import java.util.List;

/** Navigation and session composition only; no SQL or academic business rules. */
public final class MainController implements AutoCloseable {
    private final MainView view;
    private final DashboardView dashboard;
    private final DashboardService dashboardService;
    private final UiTaskRunner dashboardTasks=new UiTaskRunner();
    private long dashboardRevision;
    private boolean closed;
    private final CoursesView courses = new CoursesView();
    private final StudentsView students = new StudentsView();
    private final TermsView terms = new TermsView();
    private final CourseController courseController;
    private final StudentController studentController;
    private final TermController termController;
    private final OfferingController offeringController;
    private final EnrollmentController enrollmentController;
    private final LectureController lectureController;
    private final QuestionController questionController;
    private final ExamController examController;
    private final GradebookController gradebookController;
    private final SessionContext session = new SessionContext();
    private List<OfferingSummary> offeringChoices = List.of();

    public MainController(MainView view, CourseService service, AcademicServices academic) {
        this.view = view;
        terms.appearance(view.themes());
        dashboard = new DashboardView();
        dashboardService=new DashboardService(academic);
        offeringController = new OfferingController(academic, service, view, this::openOffering, this::updateChoices);
        courseController = new CourseController(service, courses, count -> {
            if (closed) { return; }
            dashboard.setCourseCount(count);
            offeringController.refreshChoices();
        });
        dashboard.refresh.setOnAction(e->{ courseController.refresh(); refreshDashboard(); });
        studentController = new StudentController(academic.students(), academic.enrollments(), students,academic.imports());
        termController = new TermController(academic.terms(), terms, offeringController::refreshChoices);
        enrollmentController = new EnrollmentController(academic);
        lectureController = new LectureController(academic.lectures());
        questionController = new QuestionController(academic.questions(), service);
        examController = new ExamController(academic.exams(), new ExamPdfService(academic.exams(), academic.offerings(), service));
        gradebookController = new GradebookController(academic.grades(),new ExcelExportService(academic.grades(),academic.offerings()));
        courses.onDetails(() -> offeringController.courseDetails(courses.selectedCourse()));
        courses.onCreateOffering(() -> offeringController.createForCourse(courses.selectedCourse()));
        courses.onOfferings(() -> view.showPage("Courses", offeringController.createView(null)));
        view.onOffering(this::openOffering);
        view.onNavigate(section -> {
            if(section.equals(view.currentSection()) && !section.equals("Courses")) { view.selectSection(section); return; }
            navigate(section);
        });
        navigate("Dashboard");
    }

    private void navigate(String section) {
        if (view.currentSection().equals("Grades") && !gradebookController.canLeave()) { view.selectSection("Grades"); return; }
        switch (section) {
            case "Dashboard" -> { view.showPage(section, dashboard); courseController.refresh(); refreshDashboard(); }
            case "Courses" -> { view.showPage(section, courses); courseController.refresh(); }
            case "Students" -> { view.showPage(section, students); studentController.refresh(null); }
            case "Settings" -> { view.showPage(section, terms); termController.refresh(); }
            case "Lectures", "Timeline" -> showLectures(section);
            case "Exam Bank" -> view.showPage(section, questionController.show());
            case "Exams" -> showExams();
            case "Grades" -> view.showPage(section,gradebookController.show(session.activeOffering().orElse(null)));
            default -> view.showPlaceholder(section);
        }
        offeringController.refreshChoices();
    }

    private void openOffering(OfferingSummary offering) {
        if (view.currentSection().equals("Grades") && !gradebookController.canLeave()) { view.offeringChoices(offeringChoices,session.activeOffering().orElse(null)); return; }
        session.select(offering);
        view.offeringChoices(offeringChoices, offering);
        dashboard.setActiveOffering(offering.label());
        if (view.currentSection().equals("Lectures") || view.currentSection().equals("Timeline")) {
            showLectures(view.currentSection());
        } else if (view.currentSection().equals("Exams")) { showExams(); }
        else if (view.currentSection().equals("Grades")) { view.showPage("Grades",gradebookController.show(offering)); }
        else { view.showPage("Courses", enrollmentController.createView(offering)); }
    }

    private void updateChoices(List<OfferingSummary> choices) {
        if (closed) { return; }
        offeringChoices = choices;
        OfferingSummary previous = session.activeOffering().orElse(null);
        Long activeId = session.activeOffering().map(value -> value.offering().id()).orElse(null);
        OfferingSummary updated = choices.stream().filter(value -> activeId != null && value.offering().id() == activeId).findFirst().orElse(null);
        if (updated == null) { session.clear(); } else { session.select(updated); }
        view.offeringChoices(choices, updated);
        dashboard.setActiveOffering(updated == null ? null : updated.label());
        if(!java.util.Objects.equals(previous,updated) && view.currentSection().equals("Dashboard")) { refreshDashboard(); }
        if (!java.util.Objects.equals(previous, updated) && view.currentSection().equals("Exams")) { showExams(); }
        if (!java.util.Objects.equals(previous, updated)
                && (view.currentSection().equals("Lectures") || view.currentSection().equals("Timeline"))) {
            showLectures(view.currentSection());
        }
    }

    private void showLectures(String section) {
        view.showPage(section, lectureController.show(session.activeOffering().orElse(null), section.equals("Timeline")));
    }

    private void showExams() { view.showPage("Exams", examController.show(session.activeOffering().orElse(null))); }

    @Override public void close() {
        closed=true;
        dashboardRevision++; dashboardTasks.close();
        view.themes().close();
        courseController.close(); studentController.close(); termController.close();
        offeringController.close(); enrollmentController.close();
        lectureController.close();
        questionController.close();
        examController.close();
        gradebookController.close();
    }
    public boolean canClose() { return gradebookController.canLeave(); }
    private void refreshDashboard() {
        long request=++dashboardRevision; Long offering=session.activeOffering().map(value->value.offering().id()).orElse(null);
        dashboard.loading();
        dashboardTasks.run(()->dashboardService.load(offering),value->{ if(request==dashboardRevision) { dashboard.show(value); } },message->{ if(request==dashboardRevision) { dashboard.failed(message); } });
    }
}
