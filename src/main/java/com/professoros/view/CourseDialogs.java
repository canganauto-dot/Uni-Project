package com.professoros.view;

import com.professoros.model.Course;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Objects;

public final class CourseDialogs {
    private CourseDialogs() { }

    public static boolean confirmDelete(Window owner, Course course) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(owner);
        confirmation.setTitle("Delete Course");
        confirmation.setHeaderText("Delete " + course.courseCode() + " — " + course.courseName() + "?");
        confirmation.setContentText("This action cannot be undone.");
        ButtonType delete = new ButtonType("Delete Course", ButtonBar.ButtonData.OK_DONE);
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, delete);
        style(confirmation.getDialogPane());
        Button deleteButton = (Button) confirmation.getDialogPane().lookupButton(delete);
        deleteButton.setId("confirm-delete-course");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setDefaultButton(false);
        ((Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        return confirmation.showAndWait().filter(delete::equals).isPresent();
    }

    public static void showDetails(Window owner, Course course) {
        Dialog<Void> details = new Dialog<>();
        details.initOwner(owner);
        details.setTitle("Course Details");
        details.setHeaderText(course.courseCode() + " — " + course.courseName());
        details.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        style(details.getDialogPane());
        VBox body = new VBox(14,
                DashboardView.label("Department: " + (course.department().isEmpty() ? "Not specified" : course.department()), "muted"),
                DashboardView.label("Credit hours: " + course.creditHours(), "entry-title"),
                DashboardView.label(course.description().isEmpty() ? "No description provided." : course.description(), "muted"),
                DashboardView.label("Created: " + CoursesView.formatTime(course.createdAt()), "small-muted"),
                DashboardView.label("Updated: " + CoursesView.formatTime(course.updatedAt()), "small-muted"));
        body.setMaxWidth(480);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(280);
        details.getDialogPane().setPrefWidth(540);
        details.getDialogPane().setContent(scroll);
        details.show();
    }

    static void style(DialogPane pane) {
        pane.getStylesheets().add(Objects.requireNonNull(CourseDialogs.class.getResource("/css/theme.css")).toExternalForm());
        pane.getStyleClass().add("course-dialog");
    }
}
