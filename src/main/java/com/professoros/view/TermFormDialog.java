package com.professoros.view;

import com.professoros.model.*;
import javafx.scene.control.*;
import javafx.stage.Window;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class TermFormDialog extends AcademicFormDialog {
    private final TextField name, year;
    private final DatePicker start, end;
    private final ComboBox<AcademicStatus> status = new ComboBox<>();
    public TermFormDialog(Window owner, AcademicTerm existing) {
        super(owner, existing == null ? "New Academic Term" : "Edit Academic Term");
        name = text("term-name", existing == null ? "" : existing.name());
        year = text("term-year", existing == null ? "" : existing.academicYear());
        year.setPromptText("2026–2027");
        start = date("term-start", existing == null ? null : existing.startDate());
        end = date("term-end", existing == null ? null : existing.endDate());
        status.setId("term-status");
        status.getItems().setAll(AcademicStatus.values());
        status.setValue(existing == null ? AcademicStatus.PLANNED : existing.status());
        field("Term name *", name); field("Academic year *", year); field("Start date *", start); field("End date *", end); field("Status *", status);
    }
    public AcademicTermInput input() { return new AcademicTermInput(name.getText(), year.getText(), readDate(start), readDate(end), status.getValue()); }
    private static DatePicker date(String id, LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.setId(id);
        picker.setPromptText("yyyy-MM-dd");
        picker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalDate date) { return date == null ? "" : date.toString(); }
            @Override public LocalDate fromString(String text) {
                try { return text == null || text.isBlank() ? null : LocalDate.parse(text.strip()); }
                catch (DateTimeParseException invalid) { return null; }
            }
        });
        return picker;
    }
    private static LocalDate readDate(DatePicker picker) {
        // Read the editor too: a typed date may not yet have lost focus or been committed.
        return picker.getConverter().fromString(picker.getEditor().getText());
    }
}
