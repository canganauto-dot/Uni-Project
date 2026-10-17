package com.professoros.view;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.function.Function;

/** Small visual helpers shared by the academic screens. No persistence or business rules. */
public final class AcademicControls {
    private AcademicControls() { }
    public static Button button(String text, String id, String style) { return CoursesView.button(text, id, style); }
    public static <T> TableView<T> table(String id) {
        TableView<T> table = new TableView<>();
        table.setId(id);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(180);
        table.setPrefHeight(400);
        VBox.setVgrow(table, Priority.ALWAYS);
        return table;
    }
    public static <T> void column(TableView<T> table, String title, Function<T, String> value, int width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new ReadOnlyStringWrapper(value.apply(row.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
    }
    public static VBox empty(String title, String detail, Button action) {
        VBox box = new VBox(12, DashboardView.label(title, "section-title"), DashboardView.label(detail, "muted"));
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.getStyleClass().add("empty-state");
        if (action != null) { box.getChildren().add(action); }
        return box;
    }
    public static void status(Label label, String text, boolean error) {
        label.setText(text);
        label.getStyleClass().setAll(error ? "error-message" : "muted");
    }
    public static boolean confirm(Window owner, String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(detail);
        CourseDialogs.style(alert.getDialogPane());
        ButtonType confirm = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        Button action = (Button) alert.getDialogPane().lookupButton(confirm);
        action.setId("confirm-academic-action");
        action.setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }
    public static void details(Window owner, String title, Parent content) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        CourseDialogs.style(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(720);
        dialog.getDialogPane().setContent(content);
        dialog.show();
    }
}
