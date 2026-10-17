package com.professoros.app;

import com.professoros.controller.MainController;
import com.professoros.database.Database;
import com.professoros.demo.DemoDashboard;
import com.professoros.model.*;
import com.professoros.repository.CourseRepository;
import com.professoros.service.*;
import com.professoros.support.QuestionFixture;
import com.professoros.view.MainView;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual JavaFX control workflows; uses a private database and the existing toolkit lifecycle. */
final class QuestionWorkflowCheck {
    private final Path file;
    private Stage stage;
    private MainController controller;
    private MainView root;
    QuestionWorkflowCheck(Path file) { this.file = file; }
    void verify() throws Exception {
        QuestionFixture fixture = QuestionFixture.create(file);
        try {
            open(); ready(0);
            fx(() -> {
                snapshot(root,"questions-empty.png");
                button(root,"new-question").fire(); button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> error().contains("catalog course"));
            fx(() -> {
                combo(dialog(),"question-course").getSelectionModel().selectFirst();
                text("question-text","Which concept hides implementation?");
                text("question-topic","Encapsulation"); text("question-marks","2.50");
                tabs().getSelectionModel().select(1);
                Parent choices = (Parent)tabs().getTabs().get(1).getContent();
                var inputs = choices.lookupAll("#choice-text").stream().map(TextArea.class::cast).toList();
                assertEquals(2,inputs.size()); inputs.get(0).setText("Encapsulation"); inputs.get(1).setText("Inheritance");
                button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> error().contains("exactly one"));
            fx(() -> {
                Parent choices = (Parent)tabs().getTabs().get(1).getContent();
                ((RadioButton)choices.lookupAll("#choice-correct").iterator().next()).fire();
                snapshot(dialog(),"question-mcq-form.png");
                button(dialog(),"save-academic").fire(); return null;
            });
            ready(1);
            fx(() -> {
                assertEquals(2,table().getItems().getFirst().choices().size());
                assertEquals(1,table().getItems().getFirst().choices().stream().filter(AnswerChoice::correct).count());
                button(root,"question-details").fire(); return null;
            });
            await(() -> !noDialog());
            fx(() -> {
                snapshot(dialog(),"question-details.png");
                ((Button)dialog().lookupButton(ButtonType.CLOSE)).fire();
                button(root,"edit-question").fire(); text("question-text","Explain encapsulation in Java");
                combo(dialog(),"question-difficulty").setValue(Difficulty.HARD);
                button(dialog(),"save-academic").fire(); return null;
            });
            ready(1);
            // Create every remaining type through the same form, including the True/False answer control.
            for(QuestionType type : List.of(QuestionType.TRUE_FALSE,QuestionType.SHORT_ANSWER,QuestionType.ESSAY,QuestionType.CODING)) {
                fx(() -> {
                    button(root,"new-question").fire();
                    combo(dialog(),"question-course").getSelectionModel().selectLast();
                    combo(dialog(),"question-type").setValue(type);
                    text("question-text", type + " sample prompt");
                    if(type == QuestionType.TRUE_FALSE) {
                        tabs().getSelectionModel().select(2);
                        combo(dialog(),"question-true-false").setValue("FALSE");
                    }
                    button(dialog(),"save-academic").fire(); return null;
                });
                ready(type.ordinal()+1);
            }
            fx(() -> {
                assertEquals(5,table().getItems().size());
                combo(root,"filter-question-course").getSelectionModel().selectFirst();
                combo(root,"filter-question-type").setValue(QuestionType.MCQ);
                combo(root,"filter-question-difficulty").setValue(Difficulty.HARD);
                ((TextField)root.lookup("#question-search")).setText("JAVA");
                assertEquals(1,table().getItems().size());
                combo(root,"filter-question-difficulty").setValue(Difficulty.EASY);
                assertTrue(table().getItems().isEmpty());
                button(root,"clear-question-filters").fire(); assertEquals(5,table().getItems().size());
                snapshot(root,"question-bank.png");
                stage.setWidth(1100); stage.setHeight(740); return null;
            });
            await(() -> stage.getScene().getWidth()<1101);
            fx(() -> { snapshot(root,"question-bank-minimum.png"); return null; });
            close(); open(); ready(5);
            fx(() -> {
                assertTrue(table().getItems().stream().anyMatch(q -> q.text().equals("Explain encapsulation in Java")));
                table().getSelectionModel().selectFirst(); return null;
            });
            Platform.runLater(() -> button(root,"delete-question").fire());
            await(() -> !noDialog());
            fx(() -> { ((Button)dialog().lookupButton(ButtonType.CANCEL)).fire(); return null; });
            ready(5);
            Platform.runLater(() -> button(root,"delete-question").fire());
            await(() -> !noDialog());
            fx(() -> { button(dialog(),"confirm-academic-action").fire(); return null; });
            ready(4);
            assertEquals(4,fixture.service().findAll().size());
        } finally { close(); }
    }
    private void open() throws Exception {
        Database database=new Database(file); database.initialize();
        fx(() -> {
            root=new MainView(); controller=new MainController(root,new DemoDashboard(),new CourseService(new CourseRepository(database)),AcademicServices.create(database));
            stage=new Stage(); Scene scene=new Scene(root,1360,900);
            scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm()); stage.setScene(scene); stage.show();
            ((ToggleButton)root.lookup("#nav-exam-bank")).fire(); return null;
        });
    }
    private void close() throws Exception {
        fx(() -> {
            for(Window window:List.copyOf(Window.getWindows())) { if(window instanceof Stage open) { open.close(); } }
            if(controller!=null) { controller.close(); } return null;
        });
    }
    private void ready(int count) throws Exception { await(() -> noDialog() && !button(root,"new-question").isDisabled() && table().getItems().size()==count); }
    @SuppressWarnings("unchecked") private TableView<Question> table() { return (TableView<Question>)root.lookup("#questions-table"); }
    @SuppressWarnings("unchecked") private ComboBox<Object> combo(Parent parent,String id) { return (ComboBox<Object>)parent.lookup("#"+id); }
    private TabPane tabs() { return (TabPane)dialog().lookup("#question-tabs"); }
    private DialogPane dialog() {
        return Window.getWindows().stream().filter(w -> w!=stage && w.isShowing()).map(w -> w.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
    }
    private boolean noDialog() { return Window.getWindows().stream().noneMatch(w -> w!=stage && w.isShowing()); }
    private String error() { return ((Label)dialog().lookup("#academic-form-error")).getText(); }
    private void text(String id,String value) { ((TextInputControl)dialog().lookup("#"+id)).setText(value); }
    private static Button button(Parent parent,String id) { return (Button)parent.lookup("#"+id); }
    private static void snapshot(Parent parent,String name) throws Exception {
        parent.applyCss(); parent.layout(); ApplicationSmokeTest.saveSnapshot(parent.snapshot(null,null),name);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task=new FutureTask<>(action); Platform.runLater(task); return task.get(10,TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!fx(condition)) {
            if(System.nanoTime()>deadline) { fail("Timed out waiting for question UI state"); }
            Thread.sleep(25);
        }
    }
}
