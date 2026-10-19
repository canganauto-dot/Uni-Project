package com.professoros.app;

import com.professoros.controller.MainController;
import com.professoros.database.Database;

import com.professoros.model.*;
import com.professoros.repository.CourseRepository;
import com.professoros.service.*;
import com.professoros.support.LectureFixture;
import com.professoros.view.MainView;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

final class ExamWorkflowCheck {
    private final Path file;
    private Stage stage; private MainController controller; private MainView root;
    ExamWorkflowCheck(Path file) { this.file=file; }
    void verify() throws Exception {
        var f=LectureFixture.create(file);
        var first=f.academic().questions().create(new QuestionInput(f.course().id(),QuestionType.MCQ,"Choose encapsulation","OOP",Difficulty.EASY,
                new BigDecimal("2.50"),"","Explanation","",List.of(new AnswerChoice("A",true),new AnswerChoice("B",false))));
        f.academic().questions().create(new QuestionInput(f.course().id(),QuestionType.ESSAY,"Explain inheritance","OOP",Difficulty.HARD,
                new BigDecimal("3"),"Model answer","","",List.of()));
        try {
            open();
            fx(() -> { assertTrue(button(root,"new-exam").isDisabled()); return null; });
            await(() -> selector().getItems().size()==2); choose(0); ready(0);
            fx(() -> {
                snapshot(root,"exams-empty.png"); button(root,"new-exam").fire(); return null;
            });
            await(() -> !noDialog());
            fx(() -> { button(dialog(),"save-academic").fire(); return null; });
            await(() -> error().contains("title"));
            fx(() -> {
                fill("Manual midterm"); text("exam-intended","5.50");
                combo(dialog(),"exam-difficulty-filter").setValue(Difficulty.EASY);
                assertEquals(1,bank().getItems().size()); bank().getSelectionModel().selectFirst(); button(dialog(),"add-exam-question").fire();
                button(dialog(),"clear-exam-filters").fire(); bank().getSelectionModel().selectAll(); button(dialog(),"add-exam-question").fire();
                tabs().getSelectionModel().select(1); assertEquals(2,selection().getItems().size());
                assertEquals(first.id(),selection().getItems().getFirst().questionId());
                selection().getSelectionModel().selectLast(); button(dialog(),"exam-question-up").fire();
                assertNotEquals(first.id(),selection().getItems().getFirst().questionId());
                button(dialog(),"remove-exam-question").fire(); assertEquals(1,selection().getItems().size());
                assertTrue(((Label)dialog().lookup("#exam-mark-totals")).getText().contains("Warning"));
                tabs().getSelectionModel().select(0); bank().getSelectionModel().selectAll(); button(dialog(),"add-exam-question").fire();
                tabs().getSelectionModel().select(1); selection().getSelectionModel().selectLast(); button(dialog(),"exam-question-up").fire();
                snapshot(dialog(),"exam-builder.png"); button(dialog(),"save-academic").fire(); return null;
            });
            ready(1);
            fx(() -> {
                assertFalse(table().getItems().getFirst().marksDiffer());
                button(root,"new-exam").fire(); return null;
            });
            await(() -> !noDialog());
            fx(() -> {
                fill("Generated quiz"); button(dialog(),"generate-exam").fire();
                text("generation-count","3"); button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> error().contains("cannot satisfy"));
            fx(() -> {
                text("generation-count","2"); text("quota-Easy","1"); text("quota-Hard","1");
                snapshot(dialog(),"exam-generation.png"); button(dialog(),"save-academic").fire(); return null;
            });
            await(() -> dialog().lookup("#exam-builder-tabs")!=null);
            fx(() -> {
                assertEquals(2,selection().getItems().size()); button(dialog(),"save-academic").fire(); return null;
            });
            ready(2);
            choose(1); ready(0); choose(0); ready(2);
            fx(() -> { stage.setWidth(1100); stage.setHeight(740); return null; });
            await(() -> stage.getScene().getWidth()<1101);
            fx(() -> { snapshot(root,"exams-minimum.png"); return null; });
            close(); open(); await(() -> selector().getItems().size()==2); choose(0); ready(2);
            fx(() -> {
                var manual=table().getItems().stream().filter(e -> e.title().equals("Manual midterm")).findFirst().orElseThrow();
                assertEquals(2,manual.questions().size()); assertNotEquals(first.id(),manual.questions().getFirst().questionId());
                table().getSelectionModel().select(manual); return null;
            });
            Platform.runLater(() -> button(root,"finalize-exam").fire()); await(() -> !noDialog());
            fx(() -> { button(dialog(),"confirm-academic-action").fire(); return null; }); ready(2);
            await(() -> table().getSelectionModel().getSelectedItem().status()==ExamStatus.FINALIZED);
            fx(() -> {
                assertTrue(button(root,"edit-exam").isDisabled()); button(root,"exam-details").fire(); return null;
            });
            await(() -> !noDialog());
            fx(() -> { snapshot(dialog(),"exam-details.png"); ((Button)dialog().lookupButton(ButtonType.CLOSE)).fire();
                table().getSelectionModel().select(table().getItems().stream().filter(e -> e.status()==ExamStatus.DRAFT).findFirst().orElseThrow()); return null; });
            Platform.runLater(() -> button(root,"delete-exam").fire()); await(() -> !noDialog());
            fx(() -> { ((Button)dialog().lookupButton(ButtonType.CANCEL)).fire(); return null; }); ready(2);
            Platform.runLater(() -> button(root,"delete-exam").fire()); await(() -> !noDialog());
            fx(() -> { button(dialog(),"confirm-academic-action").fire(); return null; }); ready(1);
            assertEquals(1,f.academic().questions().findById(first.id()).usageCount());
            fx(() -> { table().getSelectionModel().selectFirst(); button(root,"export-exam-pdf").fire(); return null; });
            await(() -> !noDialog());
            fx(() -> {
                text("pdf-university","Example University"); text("pdf-department","Computer Science"); text("pdf-professor","Dr. Example");
                assertTrue(button(dialog(),"save-exam-pdf").isDisabled());
                button(dialog(),"preview-exam-pdf").fire(); return null;
            });
            await(() -> !button(dialog(),"save-exam-pdf").isDisabled());
            fx(() -> {
                assertNotNull(((javafx.scene.image.ImageView)dialog().lookup("#exam-pdf-image")).getImage());
                snapshot(dialog(),"student-pdf-preview.png");
                combo(dialog(),"pdf-kind").setValue(com.professoros.pdf.PdfKind.ANSWER_KEY);
                assertTrue(button(dialog(),"save-exam-pdf").isDisabled());
                button(dialog(),"preview-exam-pdf").fire(); return null;
            });
            await(() -> !button(dialog(),"save-exam-pdf").isDisabled());
            fx(() -> { snapshot(dialog(),"answer-key-preview.png"); ((Button)dialog().lookupButton(ButtonType.CLOSE)).fire(); return null; });
        } finally { close(); }
    }
    private void fill(String title) { text("exam-title",title); text("exam-date","2026-11-15"); text("exam-duration","60"); text("exam-intended","10"); }
    private void open() throws Exception {
        Database db=new Database(file); db.initialize();
        fx(() -> {
            root=new MainView(); controller=new MainController(root,new CourseService(new CourseRepository(db)),AcademicServices.create(db));
            stage=new Stage(); Scene scene=new Scene(root,1360,900); scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            stage.setScene(scene); stage.show(); ((ToggleButton)root.lookup("#nav-exams")).fire(); return null;
        });
    }
    private void close() throws Exception { fx(() -> { for(Window w:List.copyOf(Window.getWindows())) { if(w instanceof Stage s) { s.close(); } } if(controller!=null) { controller.close(); } return null; }); }
    private void choose(int index) throws Exception { fx(() -> { selector().getSelectionModel().select(index); return null; }); }
    private void ready(int count) throws Exception { await(() -> noDialog() && !button(root,"new-exam").isDisabled() && table().getItems().size()==count); }
    @SuppressWarnings("unchecked") private ComboBox<OfferingSummary> selector() { return (ComboBox<OfferingSummary>)root.lookup("#active-offering"); }
    @SuppressWarnings("unchecked") private ComboBox<Object> combo(Parent p,String id) { return (ComboBox<Object>)p.lookup("#"+id); }
    @SuppressWarnings("unchecked") private TableView<Exam> table() { return (TableView<Exam>)root.lookup("#exams-table"); }
    @SuppressWarnings("unchecked") private TableView<Question> bank() { return (TableView<Question>)dialog().lookup("#exam-bank-questions"); }
    @SuppressWarnings("unchecked") private TableView<ExamQuestion> selection() { return (TableView<ExamQuestion>)dialog().lookup("#exam-selected-questions"); }
    private TabPane tabs() { return (TabPane)dialog().lookup("#exam-builder-tabs"); }
    private DialogPane dialog() { return Window.getWindows().stream().filter(w -> w!=stage && w.isShowing()).map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).toList().getLast(); }
    private boolean noDialog() { return Window.getWindows().stream().noneMatch(w -> w!=stage && w.isShowing()); }
    private String error() { return ((Label)dialog().lookup("#academic-form-error")).getText(); }
    private void text(String id,String value) { ((TextInputControl)dialog().lookup("#"+id)).setText(value); }
    private static Button button(Parent p,String id) { return (Button)p.lookup("#"+id); }
    private static void snapshot(Parent p,String name) throws Exception { p.applyCss(); p.layout(); ApplicationSmokeTest.saveSnapshot(p.snapshot(null,null),name); }
    private static <T> T fx(Callable<T> action) throws Exception { FutureTask<T> task=new FutureTask<>(action); Platform.runLater(task); return task.get(10,TimeUnit.SECONDS); }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!fx(condition)) { if(System.nanoTime()>deadline) { fail("Timed out waiting for exam UI state"); } Thread.sleep(25); }
    }
}
