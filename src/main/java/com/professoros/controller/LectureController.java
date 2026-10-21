package com.professoros.controller;

import com.professoros.model.*;
import com.professoros.service.LectureService;
import com.professoros.view.*;

/** Captures offering identity per operation and ignores stale loads after context changes. */
public final class LectureController implements AutoCloseable {
    private final LectureService service;
    private final LecturesView lectures = new LecturesView();
    private final UiTaskRunner tasks = new UiTaskRunner();
    private OfferingSummary offering;
    private LecturesView activeView;
    private long revision;

    public LectureController(LectureService service) {
        this.service = service;
        lectures.create.setOnAction(event -> edit(lectures, null));
        lectures.edit.setOnAction(event -> { if (lectures.selected() != null) { edit(lectures, lectures.selected()); } });
        lectures.delete.setOnAction(event -> delete(lectures));
        lectures.refresh.setOnAction(event -> load(lectures, null));
    }
    public LecturesView show(OfferingSummary selected) {
        offering = selected;
        activeView = lectures;
        revision++;
        if (selected == null) { activeView.noOffering(); }
        else { activeView.beginOffering(selected.label()); load(activeView, null); }
        return activeView;
    }
    private void load(LecturesView view, Long selectId) {
        if (offering == null) { return; }
        long offeringId = offering.offering().id();
        long request = ++revision;
        view.setBusy(true);
        tasks.run(() -> service.overview(offeringId), result -> {
            if (request == revision) { view.showOverview(result, selectId); }
        }, message -> { if (request == revision) { view.loadError(message); } });
    }
    private void edit(LecturesView view, Lecture existing) {
        if (offering == null) { return; }
        long offeringId = offering.offering().id();
        LectureFormDialog dialog = new LectureFormDialog(view.getScene().getWindow(), offering.label(), existing);
        dialog.onSave(() -> {
            LectureInput input = dialog.input(); dialog.setBusy(true);
            tasks.run(() -> existing == null ? service.create(offeringId, input) : service.update(offeringId, existing.id(), input), saved -> {
                dialog.setBusy(false); dialog.close();
                if (offering != null && offering.offering().id() == offeringId && activeView == view) { load(view, saved.id()); }
            }, message -> { dialog.setBusy(false); dialog.showError(message); });
        });
        dialog.show();
    }
    private void delete(LecturesView view) {
        Lecture selected = view.selected();
        if (selected == null || offering == null) { return; }
        long offeringId = offering.offering().id();
        if (!AcademicControls.confirm(view.getScene().getWindow(), "Delete lecture " + selected.lectureNumber() + "?",
                selected.title() + "\nThis permanently removes the lecture and professor notes. This action cannot be undone.")) { return; }
        long request = ++revision;
        view.setBusy(true);
        tasks.run(() -> { service.delete(offeringId, selected.id()); return true; }, ignored -> {
            if (request == revision) { load(view, null); }
        }, message -> { if (request == revision) { view.operationError(message); } });
    }
    @Override public void close() { revision++; tasks.close(); }
}
