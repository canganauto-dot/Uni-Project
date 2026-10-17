package com.professoros.view;

import com.professoros.model.Student;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.stage.Window;
import java.util.List;

public final class EnrollmentFormDialog extends AcademicFormDialog {
    private final ComboBox<Student> student = new ComboBox<>();
    public EnrollmentFormDialog(Window owner, List<Student> students) {
        super(owner, "Enroll Existing Student");
        TextField search = text("enrollment-search", "");
        search.setPromptText("Search name, number or email");
        FilteredList<Student> choices = new FilteredList<>(FXCollections.observableArrayList(students));
        student.setItems(choices);
        student.setId("enrollment-student");
        student.setPromptText("Choose a student");
        search.textProperty().addListener((obs, old, value) -> choices.setPredicate(s -> s.matches(value)));
        field("Search", search); field("Student *", student);
        field("Offering", new Label("Enrolls in the currently open offering."));
        if (students.isEmpty()) {
            field("No students", new Label("Create a student in Students first."));
        }
    }
    public long studentId() { return student.getValue() == null ? 0 : student.getValue().id(); }
}
