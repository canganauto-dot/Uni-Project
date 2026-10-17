package com.professoros.controller;

import com.professoros.model.*;
import com.professoros.service.LectureService;
import com.professoros.view.*;

/** Captures offering identity per operation and ignores stale loads after context changes. */
public final class LectureController implements AutoCloseable {
    private final LectureService service;
    private final LecturesView lectures = new LecturesView(false);
    private final LecturesView timeline = new LecturesView(true);
    private final UiTaskRunner tasks = new UiTaskRunner();
    private OfferingSummary offering;
    private LecturesView activeView;
    private long revision;

    public LectureController(LectureService service) {
        this.service = service;
        for (LecturesView view : java.util.List.of(lectures, timeline)) {
            view.create.setOnAction(event -> edit(view, null));
            view.edit.setOnAction(event -> { if (view.selected() != null) { edit(view, view.selected()); } });
            view.delete.setOnAction(event -> delete(view));
            view.refresh.setOnAction(event -> load(view, null));
        }
    }
    public LecturesView show(OfferingSummary selected, boolean chronological) {
        offering = selected;
        activeView = chronological ? timeline : lectures;
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
