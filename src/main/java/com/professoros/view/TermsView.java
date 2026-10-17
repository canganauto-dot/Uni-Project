package com.professoros.view;

import com.professoros.model.AcademicTerm;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class TermsView extends VBox {
    public final TableView<AcademicTerm> table = AcademicControls.table("terms-table");
    public final Button create = AcademicControls.button("+ New Term", "new-term", "primary-button");
    public final Button edit = AcademicControls.button("Edit", "edit-term", "secondary-button");
    public final Button delete = AcademicControls.button("Delete", "delete-term", "danger-button");
    public final Button refresh = AcademicControls.button("Refresh", "refresh-terms", "secondary-button");
    public final Label status = DashboardView.label("", "muted");
    public TermsView() {
        super(18); setId("terms-page"); getStyleClass().add("page");
        AcademicControls.column(table, "Term", AcademicTerm::name, 160);
        AcademicControls.column(table, "Academic Year", AcademicTerm::academicYear, 160);
        AcademicControls.column(table, "Start", t -> t.startDate().toString(), 130);
        AcademicControls.column(table, "End", t -> t.endDate().toString(), 130);
        AcademicControls.column(table, "Status", t -> t.status().toString(), 130);
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(edit.disableProperty());
        Button add = AcademicControls.button("Create Term", "empty-create-term", "primary-button");
        add.setOnAction(event -> create.fire());
        table.setPlaceholder(AcademicControls.empty("No academic terms yet.", "Define a term before creating a course offering.", add));
        getChildren().addAll(DashboardView.label("Academic Terms", "page-title"),
                DashboardView.label("Settings · Fall, spring and summer sessions share the same term model.", "muted"),
                new HBox(10, create, edit, delete, refresh), table, status);
    }
}
