package com.professoros.controller;

import com.professoros.model.*;
import com.professoros.service.*;
import com.professoros.view.*;
import javafx.scene.control.*;
import javafx.stage.Window;
import java.util.List;
import java.util.function.Consumer;

public final class OfferingController implements AutoCloseable {
    private final CourseOfferingService service;
    private final AcademicTermService terms;
    private final CourseService courses;
    private final MainView main;
    private final Consumer<OfferingSummary> open;
    private final Consumer<List<OfferingSummary>> choices;
    private final UiTaskRunner tasks = new UiTaskRunner();
    public OfferingController(AcademicServices services, CourseService courses, MainView main,
                              Consumer<OfferingSummary> open, Consumer<List<OfferingSummary>> choices) {
        this.service = services.offerings(); this.terms = services.terms(); this.courses = courses;
        this.main = main; this.open = open; this.choices = choices;
    }
    public void refreshChoices() {
        tasks.run(service::summaries, choices, message -> main.offeringError(message));
    }
    public OfferingsView createView(Long courseId) {
        OfferingsView view = new OfferingsView();
        view.create.setOnAction(event -> edit(view, courseId, null));
        view.edit.setOnAction(event -> {
            var row = view.table.getSelectionModel().getSelectedItem();
            if (row != null) { edit(view, courseId, row.offering()); }
        });
        view.open.setOnAction(event -> {
            var row = view.table.getSelectionModel().getSelectedItem();
            if (row != null) {
                Window window = view.getScene().getWindow();
                if (window != main.getScene().getWindow()) { window.hide(); }
                open.accept(row);
            }
        });
        view.delete.setOnAction(event -> delete(view, courseId));
        view.refresh.setOnAction(event -> refresh(view, courseId));
        refresh(view, courseId);
        return view;
    }
    public void courseDetails(Course course) {
        if (course != null) { CourseDialogs.showDetails(main.getScene().getWindow(), course, createView(course.id())); }
    }
    public void createForCourse(Course course) {
        if (course == null) { return; }
        OfferingsView view = createView(course.id());
        main.showPage("Courses", view);
        edit(view, course.id(), null);
    }
    private void refresh(OfferingsView view, Long courseId) {
        view.setDisable(true);
        tasks.run(service::summaries, rows -> {
            view.setDisable(false);
            view.table.getItems().setAll(rows.stream().filter(row -> courseId == null || row.offering().courseId() == courseId).toList());
            AcademicControls.status(view.status, view.table.getItems().size() + " offerings · Open one to manage enrollment.", false);
            choices.accept(rows);
        }, message -> error(view, message));
    }
    private record FormChoices(List<Course> courses, List<AcademicTerm> terms) { }
    private void edit(OfferingsView view, Long courseId, CourseOffering existing) {
        view.setDisable(true);
        tasks.run(() -> new FormChoices(courses.findAll(), terms.findAll()), options -> {
            view.setDisable(false);
            if (options.courses().isEmpty() || options.terms().isEmpty()) {
                AcademicControls.status(view.status, "Create a catalog course and an academic term (Settings) before creating an offering.", true);
                return;
            }
            OfferingFormDialog dialog = new OfferingFormDialog(view.getScene().getWindow(), options.courses(), options.terms(), existing, courseId);
            dialog.onSave(() -> {
                CourseOfferingInput input = dialog.input(); dialog.setBusy(true);
                tasks.run(() -> existing == null ? service.create(input) : service.update(existing.id(), input), saved -> {
                    dialog.setBusy(false); dialog.close(); refresh(view, courseId);
                }, message -> { dialog.setBusy(false); dialog.showError(message); });
            });
            dialog.show();
        }, message -> error(view, message));
    }
    private void delete(OfferingsView view, Long courseId) {
        OfferingSummary selected = view.table.getSelectionModel().getSelectedItem();
        if (selected == null || !AcademicControls.confirm(view.getScene().getWindow(), "Delete offering?",
                selected.label() + "\nThis action cannot be undone. Offerings with enrollment history cannot be deleted.")) { return; }
        view.setDisable(true);
        tasks.run(() -> { service.delete(selected.offering().id()); return true; }, ignored -> refresh(view, courseId),
                message -> error(view, message));
    }
    private static void error(OfferingsView view, String message) { view.setDisable(false); AcademicControls.status(view.status, message, true); }
    @Override public void close() { tasks.close(); }
}
