package com.professoros.view;

import com.professoros.model.OfferingSummary;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class OfferingsView extends VBox {
    public final TableView<OfferingSummary> table = AcademicControls.table("offerings-table");
    public final Button create = AcademicControls.button("+ Create Offering", "new-offering", "primary-button");
    public final Button open = AcademicControls.button("Open Offering", "open-offering", "primary-button");
    public final Button edit = AcademicControls.button("Edit", "edit-offering", "secondary-button");
    public final Button delete = AcademicControls.button("Delete", "delete-offering", "danger-button");
    public final Button refresh = AcademicControls.button("Refresh", "refresh-offerings", "secondary-button");
    public final Label status = DashboardView.label("", "muted");
    public OfferingsView() {
        super(16); setId("offerings-page"); getStyleClass().add("page");
        AcademicControls.column(table, "Course", o -> o.courseCode() + " — " + o.courseName(), 300);
        AcademicControls.column(table, "Term", o -> o.termName() + " " + o.academicYear(), 200);
        AcademicControls.column(table, "Section", o -> o.offering().section(), 90);
        AcademicControls.column(table, "Status", o -> o.offering().status().toString(), 110);
        open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.disableProperty().bind(open.disableProperty()); delete.disableProperty().bind(open.disableProperty());
        Button add = AcademicControls.button("Create Offering", "empty-create-offering", "primary-button");
        add.setOnAction(event -> create.fire());
        table.setPlaceholder(AcademicControls.empty("No offerings yet.", "Choose a catalog course, term and section.", add));
        getChildren().addAll(DashboardView.label("Course Offerings", "section-title"),
                new HBox(10, create, open, edit, delete, refresh), table, status);
    }
}
