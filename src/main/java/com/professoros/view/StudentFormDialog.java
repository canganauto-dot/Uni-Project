package com.professoros.view;

import com.professoros.model.*;
import javafx.scene.control.*;
import javafx.stage.Window;

public final class StudentFormDialog extends AcademicFormDialog {
    private final TextField number, first, last, email;
    private final TextArea notes;
    public StudentFormDialog(Window owner, Student existing) {
        super(owner, existing == null ? "New Student" : "Edit Student");
        number = text("student-number", existing == null ? "" : existing.studentNumber());
        first = text("student-first", existing == null ? "" : existing.firstName());
        last = text("student-last", existing == null ? "" : existing.lastName());
        email = text("student-email", existing == null ? "" : existing.email());
        notes = notes("student-notes", existing == null ? "" : existing.notes());
        field("Student number *", number); field("First name *", first); field("Last name *", last);
        field("Email", email); field("Notes", notes);
    }
    public StudentInput input() { return new StudentInput(number.getText(), first.getText(), last.getText(), email.getText(), notes.getText()); }
}
