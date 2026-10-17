package com.professoros.controller;

import com.professoros.model.*;
import com.professoros.service.*;
import com.professoros.view.*;
import javafx.scene.control.TableRow;
import java.util.List;

public final class QuestionController implements AutoCloseable {
    private final QuestionService service;
    private final CourseService courses;
    private final QuestionsView view = new QuestionsView();
    private final UiTaskRunner tasks = new UiTaskRunner();
    private record Bank(List<Question> questions, List<Course> courses) { }
    public QuestionController(QuestionService service, CourseService courses) {
        this.service = service; this.courses = courses;
        view.create.setOnAction(event -> edit(null));
        view.edit.setOnAction(event -> { if (selected() != null) { edit(selected()); } });
        view.refresh.setOnAction(event -> refresh(null));
        view.delete.setOnAction(event -> delete());
        view.details.setOnAction(event -> details());
        view.table.setRowFactory(table -> {
            TableRow<Question> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) { edit(row.getItem()); } });
            return row;
        });
    }
    public QuestionsView show() { refresh(null); return view; }
    public void refresh(Long selected) {
        view.setDisable(true); AcademicControls.status(view.status, "Loading questions…", false);
        tasks.run(() -> new Bank(service.findAll(), courses.findAll()), bank -> {
            view.setDisable(false); view.showQuestions(bank.questions(), bank.courses(), selected);
        }, message -> { view.setDisable(false); view.loadError(message); });
    }
    private Question selected() { return view.table.getSelectionModel().getSelectedItem(); }
    private void edit(Question existing) {
        QuestionFormDialog dialog = new QuestionFormDialog(view.getScene().getWindow(), view.courses(), view.course.getValue(), existing);
        dialog.onSave(() -> {
            QuestionInput input = dialog.input(); dialog.setBusy(true);
            tasks.run(() -> existing == null ? service.create(input) : service.update(existing.id(), input), saved -> {
                dialog.setBusy(false); dialog.close(); refresh(saved.id());
            }, message -> { dialog.setBusy(false); dialog.showError(message); });
        });
        dialog.show();
    }
    private void details() {
        Question selected = selected(); if (selected == null) { return; }
        view.setDisable(true);
        tasks.run(() -> service.findById(selected.id()), question -> {
            view.setDisable(false); view.showDetails(view.getScene().getWindow(), question);
        }, message -> { view.setDisable(false); AcademicControls.status(view.status, message, true); });
    }
    private void delete() {
        Question selected = selected();
        if (selected == null || !AcademicControls.confirm(view.getScene().getWindow(), "Delete question?",
                "The question and its answer choices will be permanently deleted. This action cannot be undone.")) { return; }
        view.setDisable(true);
        tasks.run(() -> { service.delete(selected.id()); return true; }, ignored -> refresh(null),
                message -> { view.setDisable(false); AcademicControls.status(view.status, message, true); });
    }
    @Override public void close() { tasks.close(); }
}
