package com.professoros.view;

import com.professoros.model.*;
import javafx.scene.control.*;
import javafx.stage.Window;
import javafx.util.StringConverter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class LectureFormDialog extends AcademicFormDialog {
    private final TextField number, title, duration;
    private final DatePicker date = new DatePicker();
    private final ComboBox<LectureStatus> status = new ComboBox<>();
    private final TextArea topics, description, professorNotes;

    public LectureFormDialog(Window owner, String offeringLabel, Lecture existing) {
        super(owner, existing == null ? "New Lecture" : "Edit Lecture");
        getDialogPane().setPrefWidth(650);
        number = text("lecture-number", existing == null ? "" : Integer.toString(existing.lectureNumber()));
        title = text("lecture-title", existing == null ? "" : existing.title());
        duration = text("lecture-duration", existing == null ? "60" : Integer.toString(existing.durationMinutes()));
        date.setId("lecture-date"); date.setPromptText("yyyy-MM-dd");
        date.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate value) { return value == null ? "" : value.toString(); }
            @Override public LocalDate fromString(String value) {
                try { return value == null || value.isBlank() ? null : LocalDate.parse(value.strip()); }
                catch (DateTimeParseException invalid) { return null; } // Service supplies the validation message.
            }
        });
        if (existing != null) { date.setValue(existing.plannedDate()); }
        status.setId("lecture-status"); status.getItems().setAll(LectureStatus.values());
        status.setValue(existing == null ? LectureStatus.PLANNED : existing.status());
        topics = notes("lecture-topics", existing == null ? "" : existing.topics());
        description = notes("lecture-description", existing == null ? "" : existing.description());
        professorNotes = notes("lecture-notes", existing == null ? "" : existing.professorNotes());
        TabPane details = new TabPane(new Tab("Topics", topics), new Tab("Description", description), new Tab("Professor notes", professorNotes));
        details.setId("lecture-text-tabs");
        details.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        details.setPrefHeight(160);
        field("Offering", DashboardView.label(offeringLabel, "small-muted"));
        field("Lecture number *", number); field("Title *", title); field("Planned date *", date);
        field("Duration (min) *", duration); field("Status *", status); field("Details", details);
        setOnShown(event -> javafx.application.Platform.runLater(number::requestFocus));
    }

    public LectureInput input() {
        return new LectureInput(integer(number.getText()), title.getText(),
                date.getConverter().fromString(date.getEditor().getText()), topics.getText(), description.getText(),
                integer(duration.getText()), status.getValue(), professorNotes.getText());
    }
    private static Integer integer(String value) {
        try { return Integer.valueOf(value.strip()); }
        catch (NumberFormatException invalid) { return null; }
    }
}
