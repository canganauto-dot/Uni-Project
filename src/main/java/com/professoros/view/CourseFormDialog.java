package com.professoros.view;

import com.professoros.model.Course;
import com.professoros.model.CourseInput;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

/** Shared create/edit form. Only text-to-number conversion happens here; service validates values. */
public final class CourseFormDialog extends Dialog<Void> {
    private final TextField code = field("course-code", "e.g. CS201");
    private final TextField name = field("course-name", "e.g. Object Oriented Programming");
    private final TextField department = field("course-department", "Optional");
    private final TextField credits = field("course-credits", "1–30");
    private final TextArea description = new TextArea();
    private final Label error = DashboardView.label("", "error-message");
    private final GridPane fields = new GridPane();
    private final Button save;
    private final Button cancel;
    private boolean busy;

    public CourseFormDialog(Window owner, Course existing) {
        initOwner(owner); setResizable(true);
        setTitle(existing == null ? "New Course" : "Edit Course");
        setHeaderText(existing == null ? "Create a course" : "Edit " + existing.courseCode());
        CourseDialogs.style(getDialogPane());
        getDialogPane().setPrefWidth(540);
        ButtonType saveType = new ButtonType("Save Course", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        save = (Button) getDialogPane().lookupButton(saveType);
        save.setId("save-course");
        save.getStyleClass().add("primary-button");
        save.setDefaultButton(true);
        cancel = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        cancel.setCancelButton(true);
        fields.getStyleClass().add("form-grid");
        fields.setHgap(16);
        fields.setVgap(14);
        ColumnConstraints labels = new ColumnConstraints(110);
        ColumnConstraints inputs = new ColumnConstraints();
        inputs.setHgrow(Priority.ALWAYS);
        inputs.setMinWidth(0);
        fields.getColumnConstraints().addAll(labels, inputs);
        addField(0, "Course Code *", code);
        addField(1, "Course Name *", name);
        addField(2, "Department", department);
        addField(3, "Credit Hours *", credits);
        description.setId("course-description");
        description.setPromptText("Optional course description");
        description.setPrefRowCount(4);
        description.setWrapText(true);
        addField(4, "Description", description);
        error.setId("course-form-error");
        error.setMaxWidth(470);
        error.setVisible(false);
        error.managedProperty().bind(error.visibleProperty());
        getDialogPane().setContent(new VBox(14, DashboardView.label("* Required fields · Course codes must be unique.", "small-muted"), fields, error));
        if (existing != null) {
            code.setText(existing.courseCode());
            name.setText(existing.courseName());
            department.setText(existing.department());
            credits.setText(Integer.toString(existing.creditHours()));
            description.setText(existing.description());
        } else {
            credits.setText("3");
        }
        setOnShown(event -> Platform.runLater(code::requestFocus));
        setOnCloseRequest(event -> {
            if (busy) {
                event.consume(); // Keep a write in flight from being mistaken for a cancelled form.
            }
        });
    }

    public void onSave(Consumer<CourseInput> action) {
        save.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); // Close explicitly only after persistence succeeds.
            if (!busy) {
                action.accept(input());
            }
        });
    }

    public void setBusy(boolean value) {
        busy = value;
        fields.setDisable(value);
        save.setDisable(value);
        cancel.setDisable(value);
        save.setText(value ? "Saving…" : "Save Course");
    }

    public void showError(String message) {
        error.setText(message);
        error.setVisible(true);
        getDialogPane().getScene().getWindow().sizeToScene();
    }

    private CourseInput input() {
        Integer hours;
        try {
            hours = Integer.valueOf(credits.getText().strip());
        } catch (NumberFormatException invalidNumber) {
            hours = null; // The service returns the consistent user-facing validation message.
        }
        return new CourseInput(code.getText(), name.getText(), department.getText(), hours, description.getText());
    }

    private void addField(int row, String title, Control control) {
        Label label = DashboardView.label(title, "form-label");
        label.setLabelFor(control);
        control.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(control, javafx.scene.layout.Priority.ALWAYS);
        fields.addRow(row, label, control);
    }

    private static TextField field(String id, String prompt) {
        TextField field = new TextField();
        field.setId(id);
        field.setPromptText(prompt);
        return field;
    }
}
