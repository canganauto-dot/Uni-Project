package com.professoros.view;

import com.professoros.model.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Locale;

/** Two presentations of the same offering-scoped rows; progress always describes the full offering. */
public final class LecturesView extends VBox {
    public final Button create = AcademicControls.button("+ New Lecture", "new-lecture", "primary-button");
    public final Button edit = AcademicControls.button("Edit", "edit-lecture", "secondary-button");
    public final Button delete = AcademicControls.button("Delete", "delete-lecture", "danger-button");
    public final Button refresh = AcademicControls.button("Refresh", "refresh-lectures", "secondary-button");
    public final TextField search = new TextField();
    public final TableView<Lecture> table = AcademicControls.table("lectures-table");
    public final ListView<Lecture> timeline = new ListView<>();
    public final Label status = DashboardView.label("", "muted");
    private final Label context = DashboardView.label("", "muted");
    private final Label progress = DashboardView.label("", "section-title");
    private final Label schedule = DashboardView.label("", "muted");
    private final ProgressBar bar = new ProgressBar(0);
    private final ObservableList<Lecture> rows = FXCollections.observableArrayList();
    private final FilteredList<Lecture> filtered = new FilteredList<>(rows);
    private final boolean chronological;
    private boolean busy, selectedOffering, failed;

    public LecturesView(boolean chronological) {
        super(16);
        this.chronological = chronological;
        setId(chronological ? "timeline-page" : "lectures-page"); getStyleClass().addAll("page","feature-page");
        context.setId("lecture-context"); progress.setId("lecture-progress"); schedule.setId("lecture-schedule");
        status.setId("lecture-message"); timeline.setId("lecture-timeline");
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
        timeline.setItems(filtered); timeline.setMinHeight(200); VBox.setVgrow(timeline, Priority.ALWAYS);
        timeline.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Lecture lecture, boolean empty) {
                super.updateItem(lecture, empty); setText(null); setGraphic(null);
                if (!empty && lecture != null) {
                    Label date = DashboardView.label(lecture.plannedDate().toString(), "timeline-date");
                    Label title = DashboardView.label("#" + lecture.lectureNumber() + " · " + lecture.title(), "section-title");
                    Label detail = DashboardView.label(lecture.status() + " · " + lecture.durationMinutes() + " minutes", "muted");
                    String topic = lecture.topics().isBlank() ? "No topics added." : lecture.topics();
                    if (topic.length() > 220) { topic = topic.substring(0, 220) + "…"; }
                    Label topics = DashboardView.label(topic, "muted");
                    VBox body = new VBox(6, date, title, detail, topics);
                    body.getStyleClass().add("lecture-timeline-entry");
                    body.prefWidthProperty().bind(list.widthProperty().subtract(45));
                    setGraphic(body);
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> actions());
        timeline.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> actions());
        table.setRowFactory(list -> {
            TableRow<Lecture> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) { table.getSelectionModel().select(row.getItem()); edit.fire(); }
            });
            return row;
        });
        timeline.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && selected() != null) { edit.fire(); } });
        AcademicControls.searchShortcut(this,search); AcademicControls.activateOnEnter(table,edit);
        bar.setMaxWidth(Double.MAX_VALUE); context.getStyleClass().add("context-strip");
        var summary=new VBox(8,progress,bar,schedule,
                DashboardView.label("Completed / non-cancelled lectures. Partial lectures are reported separately.","small-muted"));
        summary.getStyleClass().add("feature-summary");
        getChildren().addAll(AcademicControls.heading(chronological?"Lecture Timeline":"Lectures","Plan sessions and track delivery for the active offering.",create),
                context,summary,AcademicControls.filter("Find a lecture",search),
                AcademicControls.toolbar(edit,refresh,delete),chronological?timeline:table,status);
        noOffering();
    }
    public Lecture selected() { return chronological ? timeline.getSelectionModel().getSelectedItem() : table.getSelectionModel().getSelectedItem(); }
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
                if (chronological) { timeline.getSelectionModel().select(row); timeline.scrollTo(row); }
                else { table.getSelectionModel().select(row); table.scrollTo(row); }
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
        search.setDisable(unavailable); table.setDisable(unavailable); timeline.setDisable(unavailable);
        edit.setDisable(unavailable || selected() == null); delete.setDisable(unavailable || selected() == null);
    }
    private void placeholder() {
        String title = !selectedOffering ? "Select a course offering." : busy ? "Loading lectures…" :
                failed ? "Lectures could not be loaded." : rows.isEmpty() ? "No lectures yet." : "No matching lectures.";
        String detail = !selectedOffering ? "Lectures and timeline belong to an offering, not a catalog course." :
                failed ? "Use Refresh to try again." : rows.isEmpty() ? "Plan your first lecture for this offering." : "Try another number, title or topic.";
        Button add = AcademicControls.button("Create Lecture", "empty-create-lecture", "primary-button");
        add.setOnAction(event -> create.fire());
        VBox empty = AcademicControls.empty(title, detail, selectedOffering && !busy && !failed && rows.isEmpty() ? add : null);
        if (chronological) { timeline.setPlaceholder(empty); } else { table.setPlaceholder(empty); }
    }
}
