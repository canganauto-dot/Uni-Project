package com.professoros.view;

import com.professoros.model.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Locale;

/** Offering-scoped lecture table; progress always describes the full offering. */
public final class LecturesView extends VBox {
    public final Button create = AcademicControls.button("+ New Lecture", "new-lecture", "primary-button");
    public final Button edit = AcademicControls.button("Edit", "edit-lecture", "secondary-button");
    public final Button delete = AcademicControls.button("Delete", "delete-lecture", "danger-button");
    public final Button refresh = AcademicControls.button("Refresh", "refresh-lectures", "secondary-button");
    public final TextField search = new TextField();
    public final TableView<Lecture> table = AcademicControls.table("lectures-table");
    public final Label status = DashboardView.label("", "muted");
    private final Label context = DashboardView.label("", "muted");
    private final Label progress = DashboardView.label("", "section-title");
    private final Label schedule = DashboardView.label("", "muted");
    private final ProgressBar bar = new ProgressBar(0);
    private final ObservableList<Lecture> rows = FXCollections.observableArrayList();
    private final FilteredList<Lecture> filtered = new FilteredList<>(rows);
    private boolean busy, selectedOffering, failed;

    public LecturesView() {
        super(16);
        setId("lectures-page"); getStyleClass().addAll("page","feature-page");
        context.setId("lecture-context"); progress.setId("lecture-progress"); schedule.setId("lecture-schedule");
        status.setId("lecture-message");
        search.setId("lecture-search"); search.setPromptText("Search number, title or lecture text"); search.setPrefWidth(285);
        search.textProperty().addListener((obs, old, value) -> { filtered.setPredicate(lecture -> lecture.matches(value)); placeholder(); });
        TableColumn<Lecture, Integer> numberColumn = new TableColumn<>("No.");
        numberColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(row.getValue().lectureNumber()));
        numberColumn.setPrefWidth(60); table.getColumns().add(numberColumn);
        AcademicControls.column(table, "Title", Lecture::title, 300);
        AcademicControls.column(table, "Planned Date", lecture -> lecture.plannedDate().toString(), 130);
        AcademicControls.column(table, "Minutes", lecture -> Integer.toString(lecture.durationMinutes()), 75);
        AcademicControls.column(table, "Status", lecture -> lecture.status().toString(), 170);
        SortedList<Lecture> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty()); table.setItems(sorted);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> actions());
        table.setRowFactory(list -> {
            TableRow<Lecture> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) { table.getSelectionModel().select(row.getItem()); edit.fire(); }
            });
            return row;
        });
        AcademicControls.searchShortcut(this,search); AcademicControls.activateOnEnter(table,edit);
        bar.setMaxWidth(Double.MAX_VALUE); context.getStyleClass().add("context-strip");
        var summary=new VBox(8,progress,bar,schedule,
                DashboardView.label("Completed / non-cancelled lectures. Partial lectures are reported separately.","small-muted"));
        summary.getStyleClass().add("feature-summary");
        getChildren().addAll(AcademicControls.heading("Lectures","Plan sessions and track delivery for the active offering.",create),
                context,summary,AcademicControls.filter("Find a lecture",search),
                AcademicControls.toolbar(edit,refresh,delete),table,status);
        noOffering();
    }
    public Lecture selected() { return table.getSelectionModel().getSelectedItem(); }
    public void noOffering() {
        selectedOffering = false; busy = false; failed = false; rows.clear(); search.clear();
        context.setText("Choose a course offering in the top bar to begin.");
        progress.setText("No offering selected"); schedule.setText(""); status.setText(""); bar.setProgress(0);
        actions(); placeholder();
    }
    public void beginOffering(String label) {
        selectedOffering = true; failed = false; rows.clear(); search.clear(); context.setText(label);
        progress.setText("Loading lecture progress…"); schedule.setText(""); status.setText(""); bar.setProgress(0);
        setBusy(true);
    }
    public void setBusy(boolean value) { busy = value; actions(); placeholder(); }
    public void showOverview(LectureOverview overview, Long selectId) {
        failed = false; rows.setAll(overview.lectures());
        if (selectId != null) {
            search.clear();
            rows.stream().filter(row -> row.id() == selectId).findFirst().ifPresent(row -> {
                table.getSelectionModel().select(row); table.scrollTo(row);
            });
        }
        LectureProgress data = overview.progress();
        progress.setText(String.format(Locale.ROOT, "Total %d   ·   Completed %d   ·   Partial %d   ·   Cancelled %d   ·   Completion %.0f%%",
                data.total(), data.completed(), data.partial(), data.cancelled(), data.completionPercent()));
        bar.setProgress(data.completionPercent() / 100);
        schedule.setText(data.scheduleStatus() + " · As of " + data.asOf());
        setBusy(false); AcademicControls.status(status, "Saved lectures · Progress covers the entire offering, including hidden search results.", false);
    }
    public void loadError(String message) {
        rows.clear(); failed = true; progress.setText("Lecture progress unavailable"); bar.setProgress(0); schedule.setText("");
        setBusy(false); AcademicControls.status(status, message + " Use Refresh to try again.", true);
    }
    public void operationError(String message) { setBusy(false); AcademicControls.status(status, message, true); }
    private void actions() {
        boolean unavailable = busy || !selectedOffering;
        create.setDisable(unavailable); refresh.setDisable(unavailable);
        search.setDisable(unavailable); table.setDisable(unavailable);
        edit.setDisable(unavailable || selected() == null); delete.setDisable(unavailable || selected() == null);
    }
    private void placeholder() {
        String title = !selectedOffering ? "Select a course offering." : busy ? "Loading lectures…" :
                failed ? "Lectures could not be loaded." : rows.isEmpty() ? "No lectures yet." : "No matching lectures.";
        String detail = !selectedOffering ? "Lectures belong to an offering, not a catalog course." :
                failed ? "Use Refresh to try again." : rows.isEmpty() ? "Plan your first lecture for this offering." : "Try another number, title or topic.";
        Button add = AcademicControls.button("Create Lecture", "empty-create-lecture", "primary-button");
        add.setOnAction(event -> create.fire());
        VBox empty = AcademicControls.empty(title, detail, selectedOffering && !busy && !failed && rows.isEmpty() ? add : null);
        table.setPlaceholder(empty);
    }
}
