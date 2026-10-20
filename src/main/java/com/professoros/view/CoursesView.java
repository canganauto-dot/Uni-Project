package com.professoros.view;

import com.professoros.model.Course;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

public final class CoursesView extends VBox {
    private final ObservableList<Course> courses = FXCollections.observableArrayList();
    private final FilteredList<Course> filtered = new FilteredList<>(courses);
    private final TableView<Course> table = new TableView<>();
    private final TextField search = new TextField();
    private final Button create = button("+ New Course", "new-course", "primary-button");
    private final Button emptyCreate = button("Create Course", "empty-create-course", "primary-button");
    private final Button edit = button("Edit", "edit-course", "secondary-button");
    private final Button delete = button("Delete", "delete-course", "danger-button");
    private final Button details = button("View Details", "course-details", "secondary-button");
    private final Button refresh = button("Refresh", "refresh-courses", "secondary-button");
    private final Button offerings = button("All Offerings", "all-offerings", "secondary-button");
    private final Button createOffering = button("Create Offering", "create-course-offering", "primary-button");
    private final Label status = DashboardView.label("Loading saved courses…", "muted");
    private final Label count = DashboardView.label("", "small-muted");
    private boolean busy;
    private boolean failed;

    public CoursesView() {
        super(18);
        setId("courses-page");
        getStyleClass().addAll("page","feature-page");
        search.setId("course-search");
        search.setPromptText("Search code, name or department");
        search.setAccessibleText("Search courses by code, name or department");
        search.setPrefWidth(300);
        search.textProperty().addListener((observable, oldValue, newValue) -> {
            filtered.setPredicate(course -> course.matches(newValue));
            updatePlaceholder();
        });
        var toolbar=AcademicControls.heading("Courses","Your reusable course catalog. Open an offering to manage a teaching section.",create);
        AcademicControls.searchShortcut(this,search); AcademicControls.activateOnEnter(table,edit);
        table.getStyleClass().add("record-table");
        table.setId("courses-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Course Code", Course::courseCode, 115));
        table.getColumns().add(column("Course Name", Course::courseName, 250));
        table.getColumns().add(column("Department", Course::department, 160));
        TableColumn<Course, Integer> credits = new TableColumn<>("Credit Hours");
        credits.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().creditHours()));
        credits.setPrefWidth(100);
        credits.setStyle("-fx-alignment: CENTER-RIGHT;");
        table.getColumns().add(credits);
        TableColumn<Course, Instant> updated = new TableColumn<>("Updated");
        updated.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().updatedAt()));
        updated.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Instant value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatTime(value));
            }
        });
        updated.setPrefWidth(160);
        table.getColumns().add(updated);
        SortedList<Course> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPrefHeight(460);
        table.setMinHeight(270);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateActions());
        status.setId("courses-status");
        var actions=AcademicControls.toolbar(edit,details,createOffering,offerings,refresh,delete);
        createOffering.getStyleClass().remove("primary-button"); createOffering.getStyleClass().add("secondary-button");
        getChildren().addAll(toolbar,AcademicControls.filter("Find a course",search),actions,table,count,status);
        updateActions();
    }

    public void onCreate(Runnable action) {
        create.setOnAction(event -> action.run());
        emptyCreate.setOnAction(event -> action.run());
    }

    public void onEdit(Runnable action) {
        edit.setOnAction(event -> action.run());
        table.setRowFactory(view -> {
            TableRow<Course> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && !busy) {
                    table.getSelectionModel().select(row.getItem());
                    action.run();
                }
            });
            return row;
        });
    }

    public void onDelete(Runnable action) { delete.setOnAction(event -> action.run()); }
    public void onDetails(Runnable action) { details.setOnAction(event -> action.run()); }
    public void onRefresh(Runnable action) { refresh.setOnAction(event -> action.run()); }
    public void onOfferings(Runnable action) { offerings.setOnAction(event -> action.run()); }
    public void onCreateOffering(Runnable action) { createOffering.setOnAction(event -> action.run()); }
    public Course selectedCourse() { return table.getSelectionModel().getSelectedItem(); }

    public void showCourses(List<Course> result, Long selectId) {
        failed = false;
        courses.setAll(result);
        if (selectId != null) {
            search.clear();
            result.stream().filter(course -> course.id() == selectId).findFirst().ifPresent(course -> {
                table.getSelectionModel().select(course);
                table.scrollTo(course);
            });
        }
        updatePlaceholder();
    }

    public void setBusy(boolean value) {
        busy = value;
        create.setDisable(value);
        emptyCreate.setDisable(value);
        refresh.setDisable(value);
        table.setDisable(value);
        updateActions();
        updatePlaceholder();
    }

    public void showStatus(String message, boolean error) {
        failed = error;
        status.setText(message);
        status.getStyleClass().setAll(error ? "error-message" : "muted");
        updatePlaceholder();
    }

    private void updateActions() {
        boolean disabled = busy || selectedCourse() == null;
        edit.setDisable(disabled);
        delete.setDisable(disabled);
        details.setDisable(disabled);
        createOffering.setDisable(disabled);
    }

    private void updatePlaceholder() {
        count.setText(filtered.size() + " of " + courses.size() + " saved courses");
        String title = busy ? "Loading courses…" : failed ? "Courses could not be loaded." :
                courses.isEmpty() ? "No courses yet." : "No matching courses.";
        String explanation = failed ? "Use Refresh to try again." : courses.isEmpty()
                ? "Create your first course to begin organizing lectures, exams and students."
                : "Try another course code, name or department.";
        VBox empty = new VBox(12, DashboardView.label(title, "section-title"),
                DashboardView.label(explanation, "muted"));
        empty.setAlignment(Pos.CENTER);
        empty.getStyleClass().add("empty-state");
        if (!busy && !failed && courses.isEmpty()) {
            empty.getChildren().add(emptyCreate);
        }
        table.setPlaceholder(empty);
    }

    private static TableColumn<Course, String> column(String title, Function<Course, String> value, int width) {
        TableColumn<Course, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    static Button button(String text, String id, String style) {
        Button button = new Button(text);
        button.setId(id);
        button.getStyleClass().add(style);
        return button;
    }

    public static String formatTime(Instant value) {
        return DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(value);
    }
}
