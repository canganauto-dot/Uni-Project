package com.professoros.controller;

import com.professoros.model.*;
import com.professoros.service.*;
import com.professoros.view.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public final class StudentController implements AutoCloseable {
    private final StudentService service;
    private final EnrollmentService enrollments;
    private final StudentsView view;
    private final UiTaskRunner tasks = new UiTaskRunner();
    private final StudentImportController imports;
    public StudentController(StudentService service, EnrollmentService enrollments, StudentsView view,StudentImportService importService) {
        imports=new StudentImportController(importService);
        this.service = service; this.enrollments = enrollments; this.view = view;
        view.importExcel.setOnAction(e->imports.open(view.getScene().getWindow(),null,"Global student directory",()->refresh(null)));
        view.create.setOnAction(event -> edit(null));
        view.edit.setOnAction(event -> edit(view.table.getSelectionModel().getSelectedItem()));
        view.refresh.setOnAction(event -> refresh(null));
        view.delete.setOnAction(event -> delete());
        view.details.setOnAction(event -> details());
        view.table.setRowFactory(table -> {
            TableRow<Student> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) { edit(row.getItem()); } });
            return row;
        });
    }
    public void refresh(Long selected) {
        view.setDisable(true);
        AcademicControls.status(view.status, "Loading students…", false);
        tasks.run(service::findAll, rows -> {
            view.setDisable(false); view.showStudents(rows, selected);
            AcademicControls.status(view.status, rows.size() + " saved students", false);
        }, message -> { view.setDisable(false); AcademicControls.status(view.status, message, true); });
    }
    private void edit(Student existing) {
        StudentFormDialog dialog = new StudentFormDialog(view.getScene().getWindow(), existing);
        dialog.onSave(() -> {
            StudentInput input = dialog.input();
            dialog.setBusy(true);
            tasks.run(() -> existing == null ? service.create(input) : service.update(existing.id(), input), saved -> {
                dialog.setBusy(false); dialog.close(); refresh(saved.id());
            }, message -> { dialog.setBusy(false); dialog.showError(message); });
        });
        dialog.show();
    }
    private void delete() {
        Student student = view.table.getSelectionModel().getSelectedItem();
        if (student == null || !AcademicControls.confirm(view.getScene().getWindow(), "Delete " + student + "?",
                "This action cannot be undone. Students with enrollment history cannot be deleted.")) { return; }
        view.setDisable(true);
        tasks.run(() -> { service.delete(student.id()); return true; }, ignored -> refresh(null),
                message -> { view.setDisable(false); AcademicControls.status(view.status, message, true); });
    }
    private void details() {
        Student student = view.table.getSelectionModel().getSelectedItem();
        if (student == null) { return; }
        view.setDisable(true);
        tasks.run(() -> enrollments.findOfferingsForStudent(student.id()), rows -> {
            view.setDisable(false);
            VBox body = new VBox(14, new Label("Email: " + (student.email().isBlank() ? "Not specified" : student.email())),
                    new Label(student.notes().isBlank() ? "No notes." : student.notes()), new Label("Enrollment history"));
            body.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast).forEach(label -> {
                label.setWrapText(true);
                label.setMaxWidth(640);
            });
            TableView<StudentOffering> history = AcademicControls.table("student-enrollments");
            AcademicControls.column(history, "Offering", r -> r.offering().label(), 450);
            AcademicControls.column(history, "Status", r -> r.enrollment().status().toString(), 140);
            history.getItems().setAll(rows);
            history.setPlaceholder(new Label("No enrollment history."));
            body.getChildren().add(history);
            AcademicControls.details(view.getScene().getWindow(), student.toString(), body);
        }, message -> { view.setDisable(false); AcademicControls.status(view.status, message, true); });
    }
    @Override public void close() { tasks.close(); imports.close(); }
}
