package com.professoros.view;

import com.professoros.model.Student;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;

public final class StudentsView extends VBox {
    public final TableView<Student> table = AcademicControls.table("students-table");
    public final TextField search = new TextField();
    public final Button create = AcademicControls.button("+ New Student", "new-student", "primary-button");
    public final Button edit = AcademicControls.button("Edit", "edit-student", "secondary-button");
    public final Button details = AcademicControls.button("View Details", "student-details", "secondary-button");
    public final Button delete = AcademicControls.button("Delete", "delete-student", "danger-button");
    public final Button refresh = AcademicControls.button("Refresh", "refresh-students", "secondary-button");
    public final Button importExcel = AcademicControls.button("Import from Excel", "import-students-excel", "secondary-button");
    public final Label status = DashboardView.label("", "muted");
    private final ObservableList<Student> rows = FXCollections.observableArrayList();
    private final FilteredList<Student> filtered = new FilteredList<>(rows);
    public StudentsView() {
        super(18); setId("students-page"); getStyleClass().add("page");
        search.setId("student-search"); search.setPromptText("Search number, name or email"); search.setPrefWidth(300);
        search.textProperty().addListener((obs, old, text) -> { filtered.setPredicate(s -> s.matches(text)); placeholder(); });
        SortedList<Student> sorted = new SortedList<>(filtered); sorted.comparatorProperty().bind(table.comparatorProperty()); table.setItems(sorted);
        AcademicControls.column(table, "Student Number", Student::studentNumber, 150);
        AcademicControls.column(table, "Name", Student::fullName, 240);
        AcademicControls.column(table, "Email", Student::email, 240);
        AcademicControls.column(table, "Updated", s -> CoursesView.formatTime(s.updatedAt()), 170);
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        details.disableProperty().bind(edit.disableProperty()); delete.disableProperty().bind(edit.disableProperty());
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        getChildren().addAll(new HBox(12, DashboardView.label("Students", "page-title"), space, search, create),
                DashboardView.label("Student directory · Enrollment belongs to a specific offering.", "muted"),
                new HBox(10, edit, details, delete, refresh, importExcel), table, status);
        placeholder();
    }
    public void showStudents(List<Student> students, Long selectedId) {
        rows.setAll(students);
        if (selectedId != null) {
            search.clear();
            rows.stream().filter(s -> s.id() == selectedId).findFirst().ifPresent(s -> { table.getSelectionModel().select(s); table.scrollTo(s); });
        }
        placeholder();
    }
    private void placeholder() {
        Button add = AcademicControls.button("Create Student", "empty-create-student", "primary-button");
        add.setOnAction(event -> create.fire());
        table.setPlaceholder(AcademicControls.empty(rows.isEmpty() ? "No students yet." : "No matching students.",
                rows.isEmpty() ? "Create a student, then enroll them in a course offering." : "Try another name, student number or email.", rows.isEmpty() ? add : null));
    }
}
