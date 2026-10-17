package com.professoros.view;

import com.professoros.model.*;
import javafx.scene.control.*;
import javafx.stage.Window;
import java.util.List;

public final class OfferingFormDialog extends AcademicFormDialog {
    private final ComboBox<Course> course = new ComboBox<>();
    private final ComboBox<AcademicTerm> term = new ComboBox<>();
    private final TextField section;
    private final TextArea notes;
    private final ComboBox<AcademicStatus> status = new ComboBox<>();
    public OfferingFormDialog(Window owner, List<Course> courses, List<AcademicTerm> terms, CourseOffering existing, Long courseId) {
        super(owner, existing == null ? "Create Offering" : "Edit Offering");
        course.setId("offering-course"); term.setId("offering-term");
        course.getItems().setAll(courses); term.getItems().setAll(terms);
        course.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Course c) { return c == null ? "" : c.courseCode() + " — " + c.courseName(); }
            @Override public Course fromString(String text) { throw new UnsupportedOperationException("Select an existing course"); }
        });
        long selectedCourse = existing != null ? existing.courseId() : courseId == null ? 0 : courseId;
        courses.stream().filter(c -> c.id() == selectedCourse).findFirst().ifPresent(course::setValue);
        if (existing != null) { terms.stream().filter(t -> t.id() == existing.academicTermId()).findFirst().ifPresent(term::setValue); }
        section = text("offering-section", existing == null ? "" : existing.section());
        notes = notes("offering-notes", existing == null ? "" : existing.notes());
        status.setId("offering-status"); status.getItems().setAll(AcademicStatus.values());
        status.setValue(existing == null ? AcademicStatus.PLANNED : existing.status());
        field("Course *", course); field("Academic term *", term); field("Section *", section); field("Status *", status); field("Notes / schedule", notes);
    }
    public CourseOfferingInput input() {
        return new CourseOfferingInput(course.getValue() == null ? 0 : course.getValue().id(),
                term.getValue() == null ? 0 : term.getValue().id(), section.getText(), notes.getText(), status.getValue());
    }
}
