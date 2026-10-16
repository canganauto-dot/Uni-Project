package com.professoros.view;

import com.professoros.demo.DemoDashboard;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** Dashboard composition only; real services will supply data when features are implemented. */
public final class DashboardView extends VBox {
    public DashboardView(DemoDashboard data) {
        super(20);
        setId("dashboard");
        getStyleClass().add("page");
        Label eyebrow = label("YOUR TEACHING WORKSPACE", "eyebrow");
        Label title = label("A clear view of your semester.", "page-title");
        Label subtitle = label("Courses, commitments and progress — together in one place.", "muted");
        VBox heading = new VBox(7, eyebrow, title, subtitle);
        Label demoNotice = label("DEMO PREVIEW   ·   All figures and activities below are sample data, not database records.", "demo-notice");
        demoNotice.setWrapText(true);
        demoNotice.setMaxWidth(Double.MAX_VALUE);

        GridPane metrics = grid(4);
        int column = 0;
        for (var metric : data.metrics()) {
            VBox card = new VBox(9, label(metric.label(), "metric-label"),
                    label(metric.value(), "metric-value"), label(metric.detail(), "small-muted"));
            card.getStyleClass().add("metric-card");
            metrics.add(card, column++, 0);
        }

        GridPane panels = grid(2);
        panels.setVgap(18);
        panels.add(entryPanel("Today's Schedule", "A sample teaching day", data.schedule()), 0, 0);
        panels.add(entryPanel("Upcoming Deadlines", "Example dates · not live reminders", data.deadlines()), 1, 0);
        panels.add(progressPanel(data), 0, 1);
        panels.add(entryPanel("Recent Activity", "Illustrative activity only", data.activity()), 1, 1);
        getChildren().addAll(heading, demoNotice, metrics, panels);
    }

    private static GridPane grid(int columns) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        for (int i = 0; i < columns; i++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            constraint.setMinWidth(0);
            constraint.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraint);
        }
        return grid;
    }

    private static VBox panel(String title, String description) {
        VBox panel = new VBox(16);
        panel.getStyleClass().add("panel");
        panel.getChildren().add(new VBox(5, label(title, "section-title"), label(description, "small-muted")));
        return panel;
    }

    private static VBox entryPanel(String title, String description, List<DemoDashboard.Entry> entries) {
        VBox panel = panel(title, description);
        for (var entry : entries) {
            Label marker = label(entry.marker(), "entry-marker");
            marker.setMinWidth(54);
            VBox text = new VBox(5, label(entry.title(), "entry-title"), label(entry.detail(), "small-muted"));
            text.setMinWidth(0);
            HBox row = new HBox(14, marker, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("entry-row");
            HBox.setHgrow(text, Priority.ALWAYS);
            panel.getChildren().add(row);
        }
        return panel;
    }

    private static VBox progressPanel(DemoDashboard data) {
        VBox panel = panel("Course Progress", "Sample lecture completion");
        for (var progress : data.progress()) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox line = new HBox(8, label(progress.course(), "entry-title"), spacer,
                    label(Math.round(progress.fraction() * 100) + "%", "progress-value"));
            ProgressBar bar = new ProgressBar(progress.fraction());
            bar.setMaxWidth(Double.MAX_VALUE);
            panel.getChildren().add(new VBox(6, line, bar, label(progress.detail(), "small-muted")));
        }
        return panel;
    }

    static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }
}
