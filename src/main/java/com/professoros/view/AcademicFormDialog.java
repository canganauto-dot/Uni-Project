package com.professoros.view;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Shared dialog lifecycle/layout only. Entity forms and service validation remain explicit. */
public class AcademicFormDialog extends Dialog<Void> {
    private final GridPane fields = new GridPane();
    private final Label error = DashboardView.label("", "error-message");
    private final Button save;
    private boolean busy;
    private int row;

    protected AcademicFormDialog(Window owner, String title) {
        initOwner(owner);
        setTitle(title);
        setHeaderText(title);
        CourseDialogs.style(getDialogPane());
        getDialogPane().setPrefWidth(580);
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        save = (Button) getDialogPane().lookupButton(saveType);
        save.setId("save-academic");
        save.getStyleClass().add("primary-button");
        save.setDefaultButton(true);
        fields.setHgap(16);
        fields.setVgap(12);
        ColumnConstraints labels = new ColumnConstraints(125);
        ColumnConstraints inputs = new ColumnConstraints();
        inputs.setHgrow(Priority.ALWAYS);
        inputs.setMinWidth(0);
        fields.getColumnConstraints().addAll(labels, inputs);
        error.setId("academic-form-error");
        error.setVisible(false);
        error.managedProperty().bind(error.visibleProperty());
        error.setMaxWidth(510);
        getDialogPane().setContent(new VBox(14, DashboardView.label("* Required fields", "small-muted"), fields, error));
        setOnCloseRequest(event -> { if (busy) { event.consume(); } });
    }
    protected void field(String label, Control control) {
        Label caption = DashboardView.label(label, "form-label");
        caption.setLabelFor(control);
        control.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(control, Priority.ALWAYS);
        fields.addRow(row++, caption, control);
        if (row == 1) { setOnShown(event -> Platform.runLater(control::requestFocus)); }
    }
    protected static TextField text(String id, String value) {
        TextField field = new TextField(value);
        field.setId(id);
        return field;
    }
    protected static TextArea notes(String id, String value) {
        TextArea area = new TextArea(value);
        area.setId(id);
        area.setPrefRowCount(3);
        area.setWrapText(true);
        return area;
    }
    public void onSave(Runnable action) {
        save.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (!busy) { action.run(); }
        });
    }
    public void setBusy(boolean value) {
        busy = value;
        fields.setDisable(value);
        save.setDisable(value);
        getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(value);
        save.setText(value ? "Saving…" : "Save");
    }
    public void showError(String message) {
        error.setText(message);
        error.setVisible(true);
        getDialogPane().getScene().getWindow().sizeToScene();
    }
}
