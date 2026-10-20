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
    public static HBox heading(String title,String description,Button primary) {
        var copy=new VBox(5,DashboardView.label(title,"page-title"),DashboardView.label(description,"muted"));
        copy.setMinWidth(0); HBox.setHgrow(copy,Priority.ALWAYS);
        primary.setMinWidth(Region.USE_PREF_SIZE);
        var header=new HBox(20,copy,primary); header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.getStyleClass().add("feature-heading"); return header;
    }
    public static FlowPane toolbar(javafx.scene.Node... controls) {
        var bar=new FlowPane(8,8,controls); bar.getStyleClass().add("feature-toolbar"); return bar;
    }
    public static VBox filter(String caption,Control control) {
        var label=DashboardView.label(caption,"filter-caption"); label.setLabelFor(control);
        control.setAccessibleText(caption); control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5,label,control);
    }
    public static void searchShortcut(Parent page,TextField search) {
        search.setAccessibleText(search.getPromptText());
        search.setTooltip(new Tooltip("Search this page · Ctrl+F (Command+F on macOS)"));
        page.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,event->{
            if(event.isShortcutDown() && event.getCode()==javafx.scene.input.KeyCode.F) {
                search.requestFocus(); search.selectAll(); event.consume();
            }
        });
    }
    public static void activateOnEnter(TableView<?> table,Button action) {
        table.setAccessibleHelp("Use arrow keys to select a row; Enter opens the selected action.");
        table.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,event->{
            if(event.getCode()==javafx.scene.input.KeyCode.ENTER && table.isFocused()
                    && table.getSelectionModel().getSelectedItem()!=null && !action.isDisabled()) {
                action.fire(); event.consume();
            }
        });
    }
    public static Button button(String text, String id, String style) { return CoursesView.button(text, id, style); }
    public static <T> TableView<T> table(String id) {
        TableView<T> table = new TableView<>();
        table.setId(id);
        table.getStyleClass().add("record-table");
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
        column.setCellFactory(ignored->new TableCell<>() {
            @Override protected void updateItem(String text,boolean empty) {
                super.updateItem(text,empty); setText(empty?null:text);
                setTooltip(empty || text==null || text.isBlank()?null:new Tooltip(text));
            }
        });
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
