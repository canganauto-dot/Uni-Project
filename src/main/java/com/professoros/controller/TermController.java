package com.professoros.controller;

import com.professoros.model.*;
import com.professoros.service.AcademicTermService;
import com.professoros.view.*;

public final class TermController implements AutoCloseable {
    private final AcademicTermService service;
    private final TermsView view;
    private final Runnable changed;
    private final UiTaskRunner tasks = new UiTaskRunner();
    public TermController(AcademicTermService service, TermsView view, Runnable changed) {
        this.service = service; this.view = view; this.changed = changed;
        view.create.setOnAction(event -> edit(null));
        view.edit.setOnAction(event -> edit(view.table.getSelectionModel().getSelectedItem()));
        view.delete.setOnAction(event -> delete());
        view.refresh.setOnAction(event -> refresh());
    }
    public void refresh() {
        view.setDisable(true);
        tasks.run(service::findAll, rows -> {
            view.setDisable(false); view.table.getItems().setAll(rows);
            AcademicControls.status(view.status, rows.size() + " academic terms", false);
        }, this::error);
    }
    private void edit(AcademicTerm existing) {
        TermFormDialog dialog = new TermFormDialog(view.getScene().getWindow(), existing);
        dialog.onSave(() -> {
            AcademicTermInput input = dialog.input(); dialog.setBusy(true);
            tasks.run(() -> existing == null ? service.create(input) : service.update(existing.id(), input), saved -> {
                dialog.setBusy(false); dialog.close(); refresh(); changed.run();
            }, message -> { dialog.setBusy(false); dialog.showError(message); });
        });
        dialog.show();
    }
    private void delete() {
        AcademicTerm term = view.table.getSelectionModel().getSelectedItem();
        if (term == null || !AcademicControls.confirm(view.getScene().getWindow(), "Delete " + term.label() + "?",
                "This action cannot be undone. Terms with offerings cannot be deleted.")) { return; }
        view.setDisable(true);
        tasks.run(() -> { service.delete(term.id()); return true; }, ignored -> { refresh(); changed.run(); }, this::error);
    }
    private void error(String message) { view.setDisable(false); AcademicControls.status(view.status, message, true); }
    @Override public void close() { tasks.close(); }
}
