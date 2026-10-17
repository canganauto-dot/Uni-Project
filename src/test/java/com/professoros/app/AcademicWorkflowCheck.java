package com.professoros.app;

import com.professoros.controller.MainController;
import com.professoros.database.Database;
import com.professoros.demo.DemoDashboard;
import com.professoros.model.*;
import com.professoros.repository.CourseRepository;
import com.professoros.service.*;
import com.professoros.view.MainView;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real forms/controllers/services, using a private temporary database. */
final class AcademicWorkflowCheck {
    private final Path file;
    private Stage stage;
    private MainView root;
    private MainController controller;
    AcademicWorkflowCheck(Path file) { this.file = file; }

    void verify() throws Exception {
        Database database = new Database(file); database.initialize();
        CourseService courses = new CourseService(new CourseRepository(database));
        courses.create(new CourseInput("CS201", "Object Oriented Programming", "", 3, ""));
        try {
            openWorkspace();
            navigate("settings");
            await(() -> !root.lookup("#terms-page").isDisabled());
            fx(() -> {
                button(root, "new-term").fire();
                text("term-name", "Fall"); text("term-year", "2026–2027");
                ((DatePicker) dialog().lookup("#term-start")).setValue(LocalDate.of(2026,9,15));
                ((DatePicker) dialog().lookup("#term-end")).setValue(LocalDate.of(2027,1,20));
                snapshot(dialog(), "term-form.png");
                button(dialog(), "save-academic").fire();
                return null;
            });
            await(() -> noDialog() && table("terms-table").getItems().size() == 1 && !root.lookup("#terms-page").isDisabled());
            fx(() -> { snapshot(root, "terms.png"); return null; });
            navigate("courses");
            await(() -> !button(root, "new-course").isDisabled());
            fx(() -> { table("courses-table").getSelectionModel().selectFirst(); button(root, "create-course-offering").fire(); return null; });
            await(() -> !noDialog());
            fx(() -> {
                ((ComboBox<?>) dialog().lookup("#offering-term")).getSelectionModel().selectFirst();
                text("offering-section", "A");
                snapshot(dialog(), "offering-form.png");
                button(dialog(), "save-academic").fire();
                return null;
            });
            await(() -> noDialog() && table("offerings-table").getItems().size() == 1 && !root.lookup("#offerings-page").isDisabled());
            fx(() -> { snapshot(root, "offerings.png"); return null; });
            navigate("students");
            await(() -> !root.lookup("#students-page").isDisabled());
            addStudent("2026001", "Ahmed", "Ali");
            addStudent("2026002", "Sara", "Hassan");
            fx(() -> {
                TextField search = (TextField) root.lookup("#student-search");
                search.setText("ahmed ali"); assertEquals(1, table("students-table").getItems().size());
                search.clear(); snapshot(root, "students.png");
                return null;
            });
            await(() -> ((ComboBox<?>) root.lookup("#active-offering")).getItems().size() == 1);
            fx(() -> { ((ComboBox<?>) root.lookup("#active-offering")).getSelectionModel().selectFirst(); return null; });
            await(() -> root.lookup("#offering-details-page") != null && !root.lookup("#offering-details-page").isDisabled());
            fx(() -> { ((TabPane) root.lookup("#offering-details-page").lookup(".tab-pane")).getSelectionModel().select(1); return null; });
            enroll(0, 1);
            enroll(1, 2);
            fx(() -> { snapshot(root, "offering-roster.png"); button(root, "enroll-student").fire(); return null; });
            await(() -> !noDialog());
            fx(() -> { ((ComboBox<?>) dialog().lookup("#enrollment-student")).getSelectionModel().selectFirst(); button(dialog(), "save-academic").fire(); return null; });
            await(() -> ((Label) dialog().lookup("#academic-form-error")).getText().contains("already"));
            fx(() -> {
                snapshot(dialog(), "duplicate-enrollment.png");
                ((Button) dialog().lookupButton(ButtonType.CANCEL)).fire();
                return null;
            });
            navigate("dashboard");
            fx(() -> {
                assertTrue(((Label) root.lookup("#dashboard-active-offering")).getText().contains("CS201"));
                return null;
            });
            closeWorkspace();
            // Reconstruct the full application wiring, with a new session and newly opened connections.
            openWorkspace();
            await(() -> ((ComboBox<?>) root.lookup("#active-offering")).getItems().size() == 1);
            fx(() -> {
                assertNull(((ComboBox<?>) root.lookup("#active-offering")).getValue(), "Selection is session-only");
                ((ComboBox<?>) root.lookup("#active-offering")).getSelectionModel().selectFirst();
                return null;
            });
            await(() -> root.lookup("#roster-table") != null && table("roster-table").getItems().size() == 2);
            var academic = AcademicServices.create(new Database(file));
            assertEquals(1, courses.findAll().size());
            assertEquals(1, academic.terms().findAll().size());
            assertEquals(1, academic.offerings().findAll().size());
            assertEquals(2, academic.students().findAll().size());
            assertEquals(2, academic.enrollments().findStudentsForOffering(academic.offerings().findAll().getFirst().id()).size());
        } finally { closeWorkspace(); }
        // Generated fixture for a separate-process manual launch; never copy into normal app data.
        Path fixture = Path.of("target", "academic-workflow-data", "professor-os.db");
        java.nio.file.Files.createDirectories(fixture.getParent());
        java.nio.file.Files.copy(file, fixture, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void addStudent(String number, String first, String last) throws Exception {
        fx(() -> {
            button(root, "new-student").fire();
            text("student-number", number); text("student-first", first); text("student-last", last);
            text("student-email", first.toLowerCase() + "@example.edu");
            button(dialog(), "save-academic").fire(); return null;
        });
        await(() -> noDialog() && !root.lookup("#students-page").isDisabled()
                && table("students-table").getItems().stream().anyMatch(row -> ((Student) row).studentNumber().equals(number)));
    }

    private void enroll(int studentIndex, int count) throws Exception {
        fx(() -> { button(root, "enroll-student").fire(); return null; });
        await(() -> !noDialog());
        fx(() -> { ((ComboBox<?>) dialog().lookup("#enrollment-student")).getSelectionModel().select(studentIndex);
            button(dialog(), "save-academic").fire(); return null; });
        await(() -> noDialog() && table("roster-table").getItems().size() == count && !root.lookup("#offering-details-page").isDisabled());
    }

    private void openWorkspace() throws Exception {
        Database database = new Database(file); database.initialize();
        fx(() -> {
            root = new MainView();
            controller = new MainController(root, new DemoDashboard(), new CourseService(new CourseRepository(database)), AcademicServices.create(database));
            stage = new Stage(); Scene scene = new Scene(root,1360,900);
            scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            stage.setScene(scene); stage.show(); return null;
        });
    }

    private void closeWorkspace() throws Exception {
        fx(() -> {
            for (Window window : List.copyOf(Window.getWindows())) { if (window instanceof Stage open) { open.close(); } }
            if (controller != null) { controller.close(); } return null;
        });
    }
    private void navigate(String section) throws Exception {
        fx(() -> { ((ToggleButton) root.lookup("#nav-" + section)).fire(); return null; });
    }
    private TableView<?> table(String id) { return (TableView<?>) root.lookup("#" + id); }
    private DialogPane dialog() {
        return Window.getWindows().stream().filter(w -> w != stage && w.isShowing()).map(w -> w.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
    }
    private boolean noDialog() { return Window.getWindows().stream().noneMatch(w -> w != stage && w.isShowing()); }
    private void text(String id, String value) { ((TextInputControl) dialog().lookup("#" + id)).setText(value); }
    private static Button button(Parent parent, String id) { return (Button) parent.lookup("#" + id); }
    private static void snapshot(Parent parent, String name) throws Exception {
        parent.applyCss(); parent.layout(); ApplicationSmokeTest.saveSnapshot(parent.snapshot(null, null), name);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(condition)) {
            if (System.nanoTime() > deadline) { fail("Timed out waiting for academic UI state"); }
            Thread.sleep(25);
        }
    }
}
