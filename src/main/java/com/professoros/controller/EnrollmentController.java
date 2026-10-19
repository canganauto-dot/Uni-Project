package com.professoros.controller;

import com.professoros.model.*;
import com.professoros.service.*;
import com.professoros.view.*;

public final class EnrollmentController implements AutoCloseable {
    private final AcademicServices services;
    private final UiTaskRunner tasks = new UiTaskRunner();
    private final StudentImportController imports;
    public EnrollmentController(AcademicServices services) { this.services = services; imports=new StudentImportController(services.imports()); }
    public OfferingDetailsView createView(OfferingSummary summary) {
        OfferingDetailsView view = new OfferingDetailsView(summary);
        long offeringId = summary.offering().id();
        view.importExcel.setOnAction(e->imports.open(view.getScene().getWindow(),offeringId,summary.label(),()->refresh(view,offeringId)));
        view.refresh.setOnAction(event -> refresh(view, offeringId));
        view.enroll.setOnAction(event -> {
            view.setDisable(true);
            tasks.run(services.students()::findAll, students -> {
                view.setDisable(false);
                EnrollmentFormDialog dialog = new EnrollmentFormDialog(view.getScene().getWindow(), students);
                dialog.onSave(() -> {
                    long studentId = dialog.studentId(); dialog.setBusy(true);
                    tasks.run(() -> services.enrollments().enroll(studentId, offeringId), saved -> {
                        dialog.setBusy(false); dialog.close(); refresh(view, offeringId);
                    }, message -> { dialog.setBusy(false); dialog.showError(message); });
                });
                dialog.show();
            }, message -> error(view, message));
        });
        view.withdraw.setOnAction(event -> changeStatus(view, offeringId, EnrollmentStatus.WITHDRAWN));
        view.complete.setOnAction(event -> changeStatus(view, offeringId, EnrollmentStatus.COMPLETED));
        view.reactivate.setOnAction(event -> changeStatus(view, offeringId, EnrollmentStatus.ACTIVE));
        refresh(view, offeringId);
        return view;
    }
    private void refresh(OfferingDetailsView view, long offeringId) {
        view.setDisable(true);
        tasks.run(() -> services.enrollments().findStudentsForOffering(offeringId), rows -> {
            view.setDisable(false); view.showRoster(rows); AcademicControls.status(view.status, "Enrollment history is up to date.", false);
        }, message -> error(view, message));
    }
    private void changeStatus(OfferingDetailsView view, long offeringId, EnrollmentStatus status) {
        EnrolledStudent row = view.table.getSelectionModel().getSelectedItem();
        if (row == null || !AcademicControls.confirm(view.getScene().getWindow(), "Set enrollment to " + status + "?",
                row.student() + "\nEnrollment history will be preserved.")) { return; }
        view.setDisable(true);
        tasks.run(() -> services.enrollments().updateStatus(row.enrollment().id(), status),
                saved -> refresh(view, offeringId), message -> error(view, message));
    }
    private static void error(OfferingDetailsView view, String message) {
        view.setDisable(false); AcademicControls.status(view.status, message, true);
    }
    @Override public void close() { tasks.close(); imports.close(); }
}
