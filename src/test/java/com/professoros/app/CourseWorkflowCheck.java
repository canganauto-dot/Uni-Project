package com.professoros.app;

import com.professoros.controller.MainController;
import com.professoros.database.Database;
import com.professoros.demo.DemoDashboard;
import com.professoros.model.Course;
import com.professoros.repository.CourseRepository;
import com.professoros.service.CourseService;
import com.professoros.view.MainView;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Headful integration scenario invoked by the single JavaFX toolkit lifecycle in ApplicationSmokeTest. */
final class CourseWorkflowCheck {
    private final Path databaseFile;
    private Stage stage;
    private MainController controller;
    private MainView root;

    CourseWorkflowCheck(Path databaseFile) {
        this.databaseFile = databaseFile;
    }

    void verify() throws Exception {
        try {
            openWorkspace();
            await(() -> !button(root, "new-course").isDisabled());
            fx(() -> {
                assertTrue(table().getItems().isEmpty());
                assertNotNull(table().getPlaceholder().lookup("#empty-create-course"));
                snapshot(root, "courses-empty.png");
                button(root, "empty-create-course").fire();
                button(dialog(), "save-course").fire();
                return null;
            });
            await(() -> error().contains("Course code is required"));
            fx(() -> {
                fill(" cs201 ", "Object Oriented Programming", "Computing", "3", "Core programming course.");
                snapshot(dialog(), "course-form.png");
                button(dialog(), "save-course").fire();
                return null;
            });
            await(() -> noDialog() && table().getItems().size() == 1 && !button(root, "edit-course").isDisabled());
            Course created = fx(() -> table().getSelectionModel().getSelectedItem());
            assertEquals("CS201", created.courseCode());

            fx(() -> {
                button(root, "new-course").fire();
                fill("CS201", "Duplicate", "", "3", "");
                button(dialog(), "save-course").fire();
                return null;
            });
            await(() -> error().contains("CS201 already exists"));
            fx(() -> {
                snapshot(dialog(), "course-duplicate.png");
                ((Button) dialog().lookupButton(ButtonType.CANCEL)).fire();
                for (String query : List.of("cs201", "oriented", "COMPUTING")) {
                    ((TextField) root.lookup("#course-search")).setText(query);
                    assertEquals(1, table().getItems().size(), query);
                }
                ((TextField) root.lookup("#course-search")).setText("no match");
                assertTrue(table().getItems().isEmpty());
                ((TextField) root.lookup("#course-search")).clear();
                table().getSelectionModel().selectFirst();
                button(root, "edit-course").fire();
                assertEquals("CS201", ((TextField) dialog().lookup("#course-code")).getText());
                fill("CS202", "Advanced Programming", "Computer Science", "4", "Updated description.");
                button(dialog(), "save-course").fire();
                return null;
            });
            await(() -> noDialog() && !button(root, "edit-course").isDisabled()
                    && table().getItems().getFirst().courseCode().equals("CS202"));
            fx(() -> {
                Course edited = table().getItems().getFirst();
                assertEquals(created.id(), edited.id());
                assertEquals(created.createdAt(), edited.createdAt());
                assertEquals(4, edited.creditHours());
                assertFalse(edited.updatedAt().isBefore(created.updatedAt()));
                snapshot(root, "courses-populated.png");
                button(root, "course-details").fire();
                assertTrue(dialog().getHeaderText().contains("Advanced Programming"));
                snapshot(dialog(), "course-details.png");
                ((Button) dialog().lookupButton(ButtonType.CLOSE)).fire();
                return null;
            });
            closeWorkspace();
            // Recreate database/service/controller/view with no retained list or connection.
            openWorkspace();
            await(() -> !button(root, "new-course").isDisabled() && table().getItems().size() == 1);
            fx(() -> {
                assertEquals("CS202", table().getItems().getFirst().courseCode());
                ((ToggleButton) root.lookup("#nav-dashboard")).fire();
                assertEquals("1", ((Label) root.lookup("#dashboard-course-count")).getText());
                snapshot(root, "dashboard-persisted-course.png");
                ((ToggleButton) root.lookup("#nav-courses")).fire();
                return null;
            });
            await(() -> !button(root, "new-course").isDisabled());
            fx(() -> { table().getSelectionModel().selectFirst(); return null; });
            // showAndWait runs a nested event loop: schedule the originating button without blocking JUnit.
            Platform.runLater(() -> button(root, "delete-course").fire());
            await(() -> !noDialog() && dialog().lookup("#confirm-delete-course") != null);
            fx(() -> {
                assertTrue(dialog().getContentText().contains("cannot be undone"));
                snapshot(dialog(), "course-delete-confirmation.png");
                ((Button) dialog().lookupButton(ButtonType.CANCEL)).fire();
                return null;
            });
            await(this::noDialog);
            assertEquals(1, fx(() -> table().getItems().size()));
            Platform.runLater(() -> button(root, "delete-course").fire());
            await(() -> !noDialog() && dialog().lookup("#confirm-delete-course") != null);
            fx(() -> { button(dialog(), "confirm-delete-course").fire(); return null; });
            await(() -> noDialog() && table().getItems().isEmpty() && !button(root, "new-course").isDisabled());
            fx(() -> {
                ((ToggleButton) root.lookup("#nav-dashboard")).fire();
                assertEquals("0", ((Label) root.lookup("#dashboard-course-count")).getText());
                return null;
            });
            Database reopened = new Database(databaseFile);
            reopened.initialize();
            assertTrue(new CourseRepository(reopened).findAll().isEmpty());
        } finally {
            closeWorkspace();
        }
    }

    private void openWorkspace() throws Exception {
        Database database = new Database(databaseFile);
        database.initialize();
        fx(() -> {
            root = new MainView();
            controller = new MainController(root, new DemoDashboard(), new CourseService(new CourseRepository(database)));
            stage = new Stage();
            Scene scene = new Scene(root, 1360, 900);
            scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            ((ToggleButton) root.lookup("#nav-courses")).fire();
            return null;
        });
    }

    private void closeWorkspace() throws Exception {
        fx(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window instanceof Stage open) { open.close(); }
            }
            if (controller != null) { controller.close(); }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private TableView<Course> table() { return (TableView<Course>) root.lookup("#courses-table"); }

    private DialogPane dialog() {
        return Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).findFirst().orElseThrow();
    }

    private boolean noDialog() { return Window.getWindows().stream().noneMatch(window -> window != stage && window.isShowing()); }
    private String error() { return ((Label) dialog().lookup("#course-form-error")).getText(); }
    private static Button button(Parent parent, String id) { return (Button) parent.lookup("#" + id); }

    private void fill(String code, String name, String department, String credits, String description) {
        DialogPane pane = dialog();
        ((TextField) pane.lookup("#course-code")).setText(code);
        ((TextField) pane.lookup("#course-name")).setText(name);
        ((TextField) pane.lookup("#course-department")).setText(department);
        ((TextField) pane.lookup("#course-credits")).setText(credits);
        ((TextArea) pane.lookup("#course-description")).setText(description);
    }

    private static void snapshot(Parent parent, String name) throws Exception {
        parent.applyCss();
        parent.layout();
        ApplicationSmokeTest.saveSnapshot(parent.snapshot(null, null), name);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(condition)) {
            if (System.nanoTime() > deadline) { fail("Timed out waiting for course UI state"); }
            Thread.sleep(25);
        }
    }
}
