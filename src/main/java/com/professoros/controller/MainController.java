package com.professoros.controller;

import com.professoros.demo.DemoDashboard;
import com.professoros.model.OfferingSummary;
import com.professoros.service.*;
import com.professoros.view.*;
import java.util.List;

/** Navigation and session composition only; no SQL or academic business rules. */
public final class MainController implements AutoCloseable {
    private final MainView view;
    private final DashboardView dashboard;
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
    private final SessionContext session = new SessionContext();
    private List<OfferingSummary> offeringChoices = List.of();

    public MainController(MainView view, DemoDashboard data, CourseService service, AcademicServices academic) {
        this.view = view;
        dashboard = new DashboardView(data);
        offeringController = new OfferingController(academic, service, view, this::openOffering, this::updateChoices);
        courseController = new CourseController(service, courses, count -> {
            dashboard.setCourseCount(count);
            offeringController.refreshChoices();
        });
        studentController = new StudentController(academic.students(), academic.enrollments(), students);
        termController = new TermController(academic.terms(), terms, offeringController::refreshChoices);
        enrollmentController = new EnrollmentController(academic);
        lectureController = new LectureController(academic.lectures());
        questionController = new QuestionController(academic.questions(), service);
        examController = new ExamController(academic.exams());
        courses.onDetails(() -> offeringController.courseDetails(courses.selectedCourse()));
        courses.onCreateOffering(() -> offeringController.createForCourse(courses.selectedCourse()));
        courses.onOfferings(() -> view.showPage("Courses", offeringController.createView(null)));
        view.onOffering(this::openOffering);
        view.onNavigate(this::navigate);
        navigate("Dashboard");
    }

    private void navigate(String section) {
        switch (section) {
            case "Dashboard" -> { view.showPage(section, dashboard); courseController.refresh(); }
            case "Courses" -> { view.showPage(section, courses); courseController.refresh(); }
            case "Students" -> { view.showPage(section, students); studentController.refresh(null); }
            case "Settings" -> { view.showPage(section, terms); termController.refresh(); }
            case "Lectures", "Timeline" -> showLectures(section);
            case "Exam Bank" -> view.showPage(section, questionController.show());
            case "Exams" -> showExams();
            default -> view.showPlaceholder(section);
        }
        offeringController.refreshChoices();
    }

    private void openOffering(OfferingSummary offering) {
        session.select(offering);
        view.offeringChoices(offeringChoices, offering);
        dashboard.setActiveOffering(offering.label());
        if (view.currentSection().equals("Lectures") || view.currentSection().equals("Timeline")) {
            showLectures(view.currentSection());
        } else if (view.currentSection().equals("Exams")) { showExams(); }
        else { view.showPage("Courses", enrollmentController.createView(offering)); }
    }

    private void updateChoices(List<OfferingSummary> choices) {
        offeringChoices = choices;
        OfferingSummary previous = session.activeOffering().orElse(null);
        Long activeId = session.activeOffering().map(value -> value.offering().id()).orElse(null);
        OfferingSummary updated = choices.stream().filter(value -> activeId != null && value.offering().id() == activeId).findFirst().orElse(null);
        if (updated == null) { session.clear(); } else { session.select(updated); }
        view.offeringChoices(choices, updated);
        dashboard.setActiveOffering(updated == null ? null : updated.label());
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
        courseController.close(); studentController.close(); termController.close();
        offeringController.close(); enrollmentController.close();
        lectureController.close();
        questionController.close();
        examController.close();
    }
}
