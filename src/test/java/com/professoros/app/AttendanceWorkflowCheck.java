package com.professoros.app;
import com.professoros.controller.MainController;
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
import java.time.LocalDate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
final class AttendanceWorkflowCheck {
    private Stage stage;private MainView root;private MainController controller;private LectureFixture f;private long enrollment,lecture;
    void verify(Path path) throws Exception {
        f=LectureFixture.create(path);
        var a=f.academic();var student=a.students().create(new StudentInput("0001","Ahmed","Ali","",""));enrollment=a.enrollments().enroll(student.id(),f.first().id()).id();
        var sara=a.students().create(new StudentInput("0002","Sara","Hassan","",""));a.enrollments().enroll(sara.id(),f.first().id());
        lecture=a.lectures().create(f.first().id(),LectureFixture.input(1,LocalDate.now(),LectureStatus.COMPLETED)).id();
        a.lectures().create(f.first().id(),LectureFixture.input(2,LocalDate.now().plusDays(1),LectureStatus.PLANNED));
        try {
            open();await(()->selector().getItems().size()==2);fx(()->{selector().getSelectionModel().selectFirst();return null;});ready();
            fx(()->{assertEquals("Unmarked",cell().getValue());cell().setValue("LATE");assertFalse(button("save-attendance").isDisabled());return null;});
            assertTrue(a.attendance().load(f.first().id()).records().isEmpty());
            Platform.runLater(()->((ToggleButton)root.lookup("#nav-courses")).fire());await(()->dialog()!=null);
            fx(()->{((Button)dialog().lookupButton(ButtonType.CANCEL)).fire();assertEquals("Attendance",root.currentSection());return null;});
            Platform.runLater(()->lectures().getSelectionModel().selectLast());await(()->dialog()!=null);
            fx(()->{((Button)dialog().lookupButton(ButtonType.CANCEL)).fire();return null;});await(()->lectures().getValue().id()==lecture);
            Platform.runLater(()->selector().getSelectionModel().selectLast());await(()->dialog()!=null);
            fx(()->{((Button)dialog().lookupButton(ButtonType.CANCEL)).fire();return null;});await(()->selector().getValue().offering().id()==f.first().id());
            Platform.runLater(()->stage.fireEvent(new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST)));await(()->dialog()!=null);
            fx(()->{((Button)dialog().lookupButton(ButtonType.CANCEL)).fire();return null;});await(()->dialog()==null);
            fx(()->{assertTrue(stage.isShowing());return null;});
            fx(()->{root.themes().select(ThemeMode.DARK);assertEquals("LATE",cell().getValue());button("save-attendance").fire();return null;});ready();
            assertEquals(AttendanceStatus.LATE,a.attendance().load(f.first().id()).status(enrollment,lecture));
            // Bulk operation is confirmed and includes both students; individual edits remain possible.
            fx(()->{((TextField)root.lookup("#attendance-search")).setText("Ahmed");assertEquals(1,((TableView<?>)root.lookup("#attendance-table")).getItems().size());((ComboBox<String>)root.lookup("#attendance-bulk-status")).setValue("PRESENT");return null;});
            Platform.runLater(()->button("bulk-attendance").fire());await(()->dialog()!=null);
            fx(()->{((Button)dialog().lookup("#confirm-academic-action")).fire();return null;});await(()->!button("save-attendance").isDisabled());
            fx(()->{cell().setValue("ABSENT");button("save-attendance").fire();return null;});ready();
            assertEquals(2,a.attendance().load(f.first().id()).records().size());
            fx(()->{((TextField)root.lookup("#attendance-search")).clear();return null;});
            for(var theme:new ThemeMode[]{ThemeMode.LIGHT,ThemeMode.DARK}) for(int width:new int[]{1360,1100}) fx(()->{
                root.themes().select(theme);root.resize(width,width==1100?740:900);root.applyCss();root.layout();
                assertTrue(root.lookup("#attendance-table").getBoundsInLocal().getHeight()>170);
                ApplicationSmokeTest.saveSnapshot(root.snapshot(null,null),"attendance-"+theme+"-"+width+".png");return null;
            });
            fx(()->{((TableView<?>)root.lookup("#attendance-table")).getSelectionModel().selectFirst();button("attendance-history").fire();return null;});await(()->dialog()!=null);
            fx(()->{assertTrue(((TextArea)dialog().getContent()).getText().contains("ABSENT"));((Button)dialog().lookupButton(ButtonType.CLOSE)).fire();return null;});
            close();f.database().initialize();open();await(()->selector().getItems().size()==2);fx(()->{selector().getSelectionModel().selectFirst();return null;});ready();
            fx(()->{assertEquals("ABSENT",cell().getValue());cell().setValue("Unmarked");button("save-attendance").fire();return null;});ready();assertNull(a.attendance().load(f.first().id()).status(enrollment,lecture));
        } finally {close();}
    }
    private void open() throws Exception {fx(()->{root=new MainView();controller=new MainController(root,new CourseService(new CourseRepository(f.database())),AcademicServices.create(f.database()));stage=new Stage();stage.setScene(new Scene(root,1360,900));stage.setOnCloseRequest(e->{if(!controller.canClose())e.consume();});stage.show();((ToggleButton)root.lookup("#nav-attendance")).fire();return null;});}
    private void close() throws Exception {fx(()->{if(controller!=null)controller.close();if(stage!=null){stage.setOnCloseRequest(null);stage.close();}controller=null;stage=null;return null;});}
    private Button button(String id){return (Button)root.lookup("#"+id);}
    @SuppressWarnings("unchecked") private ComboBox<String> cell(){return (ComboBox<String>)root.lookup("#attendance-cell-"+enrollment);}
    @SuppressWarnings("unchecked") private ComboBox<Lecture> lectures(){return (ComboBox<Lecture>)root.lookup("#attendance-lecture");}
    @SuppressWarnings("unchecked") private ComboBox<OfferingSummary> selector(){return (ComboBox<OfferingSummary>)root.lookup("#active-offering");}
    private DialogPane dialog(){return Window.getWindows().stream().filter(w->w!=stage && w.isShowing()).map(w->w.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElse(null);}
    private void ready() throws Exception {await(()->dialog()==null && !button("reload-attendance").isDisabled() && cell()!=null);}
    private static <T>T fx(Callable<T> action) throws Exception {var task=new FutureTask<>(action);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!fx(condition)){if(System.nanoTime()>until)fail("Attendance UI did not become ready");Thread.sleep(25);}}
}
