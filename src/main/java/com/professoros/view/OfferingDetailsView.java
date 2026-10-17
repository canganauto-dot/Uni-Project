package com.professoros.view;

import com.professoros.model.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;

public final class OfferingDetailsView extends VBox {
    public final TableView<EnrolledStudent> table = AcademicControls.table("roster-table");
    public final Button enroll = AcademicControls.button("Enroll Student", "enroll-student", "primary-button");
    public final Button withdraw = AcademicControls.button("Withdraw", "withdraw-student", "secondary-button");
    public final Button complete = AcademicControls.button("Mark Completed", "complete-enrollment", "secondary-button");
    public final Button reactivate = AcademicControls.button("Reactivate", "reactivate-enrollment", "secondary-button");
    public final Button refresh = AcademicControls.button("Refresh", "refresh-roster", "secondary-button");
    public final Label status = DashboardView.label("", "muted");
    public final TabPane tabs = new TabPane();
    private final Label rosterCount = DashboardView.label("", "muted");
    public OfferingDetailsView(OfferingSummary summary) {
        super(18); setId("offering-details-page"); getStyleClass().add("page");
        AcademicControls.column(table, "Student Number", r -> r.student().studentNumber(), 150);
        AcademicControls.column(table, "Name", r -> r.student().fullName(), 230);
        AcademicControls.column(table, "Email", r -> r.student().email(), 230);
        AcademicControls.column(table, "Enrollment Status", r -> r.enrollment().status().toString(), 160);
        withdraw.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        complete.disableProperty().bind(withdraw.disableProperty()); reactivate.disableProperty().bind(withdraw.disableProperty());
        table.setPlaceholder(AcademicControls.empty("No enrollments yet.", "Enroll an existing student from your student directory.", null));
        VBox overview = new VBox(16, DashboardView.label(summary.courseName(), "section-title"),
                DashboardView.label("Offering status: " + summary.offering().status(), "muted"),
                DashboardView.label(summary.offering().notes().isBlank() ? "No schedule or notes added." : summary.offering().notes(), "muted"),
                DashboardView.label("This is the active teaching context. Future teaching modules will attach to this offering.", "muted"));
        overview.getStyleClass().add("page");
        VBox roster = new VBox(14, new HBox(10, enroll, withdraw, complete, reactivate, refresh), rosterCount, table, status);
        roster.setStyle("-fx-padding: 18 0 0 0;");
        Tab overviewTab = new Tab("Overview", overview);
        Tab studentsTab = new Tab("Students", roster); studentsTab.setId("offering-students-tab");
        tabs.getTabs().addAll(overviewTab, studentsTab); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().addAll(DashboardView.label(summary.label(), "section-title"), tabs);
    }
    public void showRoster(List<EnrolledStudent> rows) {
        table.getItems().setAll(rows);
        long active = rows.stream().filter(r -> r.enrollment().status() == EnrollmentStatus.ACTIVE).count();
        rosterCount.setText(active + " active students · " + rows.size() + " total enrollment records");
    }
}
