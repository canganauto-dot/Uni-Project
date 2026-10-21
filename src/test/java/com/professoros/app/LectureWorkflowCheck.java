package com.professoros.app;

import com.professoros.controller.MainController;
import com.professoros.database.Database;

import com.professoros.model.*;
import com.professoros.repository.CourseRepository;
import com.professoros.service.*;
import com.professoros.support.LectureFixture;
import com.professoros.view.MainView;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offering-scoped CRUD smoke scenario, run within ApplicationSmokeTest's toolkit lifecycle. */
final class LectureWorkflowCheck {
    private final Path file;
    private Stage stage;
    private MainController controller;
    private MainView root;
    LectureWorkflowCheck(Path file) { this.file = file; }

    void verify() throws Exception {
        LectureFixture fixture = LectureFixture.create(file);
        try {
            open();
            navigate("lectures");
            fx(() -> {
                assertTrue(button(root,"new-lecture").isDisabled());
                assertTrue(((Label)root.lookup("#lecture-progress")).getText().contains("No offering"));
                return null;
            });
            await(() -> selector().getItems().size() == 2);
            choose(0);
            await(() -> root.lookup("#lectures-page") != null && !button(root,"new-lecture").isDisabled());
            fx(() -> {
                assertTrue(table().getItems().isEmpty()); snapshot(root,"lectures-empty.png");
                button(root,"new-lecture").fire(); button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> formError().contains("Lecture number"));
            fx(() -> {
                fill(2,"Introduction",LocalDate.now().plusDays(2),LectureStatus.PLANNED);
                snapshot(dialog(),"lecture-form.png"); button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> noDialog() && !button(root,"new-lecture").isDisabled() && table().getItems().size() == 1);
            fx(() -> {
                button(root,"new-lecture").fire();
                fill(2,"Duplicate",LocalDate.now(),LectureStatus.PLANNED); button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> formError().contains("already exists"));
            fx(() -> {
                fill(1,"Classes and Objects",LocalDate.now().minusDays(1),LectureStatus.PARTIALLY_COMPLETED);
                button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> noDialog() && !button(root,"new-lecture").isDisabled() && table().getItems().size() == 2);
            fx(() -> {
                ((TextField)root.lookup("#lecture-search")).setText("INTRODUCTION");
                assertEquals(1,table().getItems().size());
                assertTrue(((Label)root.lookup("#lecture-progress")).getText().contains("Total 2"));
                ((TextField)root.lookup("#lecture-search")).clear();
                table().getSelectionModel().select(1); button(root,"edit-lecture").fire();
                text("lecture-title","Introduction revised");
                comboStatus().setValue(LectureStatus.COMPLETED);
                button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> noDialog() && !button(root,"new-lecture").isDisabled());
            fx(() -> {
                assertTrue(((Label)root.lookup("#lecture-progress")).getText().contains("Completion 50%"));
                assertTrue(((Label)root.lookup("#lecture-schedule")).getText().contains("Behind schedule"));
                snapshot(root,"lectures.png"); return null;
            });
            choose(1);
            await(() -> !button(root,"new-lecture").isDisabled() && table().getItems().isEmpty());
            fx(() -> {
                assertTrue(((Label)root.lookup("#lecture-context")).getText().contains("Section B"));
                button(root,"new-lecture").fire(); fill(2,"Section B only",LocalDate.now(),LectureStatus.PLANNED);
                button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> noDialog() && !button(root,"new-lecture").isDisabled() && table().getItems().size() == 1);
            navigate("lectures");
            await(() -> !button(root,"new-lecture").isDisabled());
            choose(0);
            await(() -> !button(root,"new-lecture").isDisabled() && table().getItems().size() == 2);
            fx(() -> {
                assertNotNull(root.lookup("#lectures-page"));
                assertEquals(1,table().getItems().getFirst().lectureNumber());
                assertEquals(2,table().getItems().getLast().lectureNumber());
                snapshot(root,"lecture-table.png");
                stage.setWidth(1100); stage.setHeight(740); return null;
            });
            // Allow native layout to settle, then inspect the minimum-size lecture table.
            await(() -> stage.getScene().getWidth() < 1101);
            fx(() -> { snapshot(root,"lecture-table-minimum.png"); table().getSelectionModel().selectFirst(); return null; });
            Platform.runLater(() -> button(root,"delete-lecture").fire());
            await(() -> !noDialog());
            fx(() -> { ((Button)dialog().lookupButton(ButtonType.CANCEL)).fire(); return null; });
            await(this::noDialog);
            fx(() -> { assertEquals(2,table().getItems().size()); return null; });
            Platform.runLater(() -> button(root,"delete-lecture").fire());
            await(() -> !noDialog());
            fx(() -> { button(dialog(),"confirm-academic-action").fire(); return null; });
            await(() -> noDialog() && !button(root,"new-lecture").isDisabled() && table().getItems().size() == 1);
            fx(() -> {
                assertTrue(((Label)root.lookup("#lecture-progress")).getText().contains("Completion 100%"));
                return null;
            });
            close();
            open(); navigate("lectures");
            await(() -> selector().getItems().size() == 2);
            choose(0);
            await(() -> !button(root,"new-lecture").isDisabled() && table().getItems().size() == 1);
            fx(() -> {
                assertEquals("Introduction revised",table().getItems().getFirst().title());
                assertEquals(LectureStatus.COMPLETED,table().getItems().getFirst().status()); return null;
            });
            assertEquals(1,fixture.academic().lectures().overview(fixture.second().id()).progress().total());
        } finally { close(); }
        Path artifact = Path.of("target","lecture-workflow-data","professor-os.db");
        Files.createDirectories(artifact.getParent()); Files.copy(file,artifact,StandardCopyOption.REPLACE_EXISTING);
    }
    private void fill(int number,String title,LocalDate date,LectureStatus status) {
        text("lecture-number",Integer.toString(number)); text("lecture-title",title);
        ((DatePicker)dialog().lookup("#lecture-date")).setValue(date);
        text("lecture-duration","75"); comboStatus().setValue(status);
        text("lecture-topics","Classes, interfaces and examples");
        // Inactive tab content is not in the scene graph; explicitly select each tab to exercise its fields.
        TabPane tabs = (TabPane)dialog().lookup("#lecture-text-tabs");
        tabs.getSelectionModel().select(1);
        ((TextArea)tabs.getTabs().get(1).getContent()).setText("Worked examples");
        tabs.getSelectionModel().select(2);
        ((TextArea)tabs.getTabs().get(2).getContent()).setText("Review questions next time");
        tabs.getSelectionModel().select(0);
    }
    private void open() throws Exception {
        Database database = new Database(file); database.initialize();
        fx(() -> {
            root = new MainView(); controller = new MainController(root,new CourseService(new CourseRepository(database)),AcademicServices.create(database));
            stage = new Stage(); Scene scene = new Scene(root,1360,900);
            scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            stage.setScene(scene); stage.show(); return null;
        });
    }
    private void close() throws Exception {
        fx(() -> {
            for (Window window : List.copyOf(Window.getWindows())) { if(window instanceof Stage open) { open.close(); } }
            if(controller != null) { controller.close(); } return null;
        });
    }
    private void navigate(String name) throws Exception { fx(() -> { ((ToggleButton)root.lookup("#nav-"+name)).fire(); return null; }); }
    private void choose(int index) throws Exception { fx(() -> { selector().getSelectionModel().select(index); return null; }); }
    @SuppressWarnings("unchecked") private ComboBox<OfferingSummary> selector() { return (ComboBox<OfferingSummary>)root.lookup("#active-offering"); }
    @SuppressWarnings("unchecked") private ComboBox<LectureStatus> comboStatus() { return (ComboBox<LectureStatus>)dialog().lookup("#lecture-status"); }
    @SuppressWarnings("unchecked") private TableView<Lecture> table() { return (TableView<Lecture>)root.lookup("#lectures-table"); }
    private DialogPane dialog() {
        return Window.getWindows().stream().filter(w -> w != stage && w.isShowing()).map(w -> w.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
    }
    private boolean noDialog() { return Window.getWindows().stream().noneMatch(w -> w != stage && w.isShowing()); }
    private String formError() { return ((Label)dialog().lookup("#academic-form-error")).getText(); }
    private void text(String id,String value) { ((TextInputControl)dialog().lookup("#"+id)).setText(value); }
    private static Button button(Parent parent,String id) { return (Button)parent.lookup("#"+id); }
    private static void snapshot(Parent parent,String name) throws Exception {
        parent.applyCss(); parent.layout(); ApplicationSmokeTest.saveSnapshot(parent.snapshot(null,null),name);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(10,TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!fx(condition)) {
            if(System.nanoTime()>deadline) { fail("Timed out waiting for lecture UI state"); }
            Thread.sleep(25);
        }
    }
}
