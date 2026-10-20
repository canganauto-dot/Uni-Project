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
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

final class GradeWorkflowCheck {
    private final Path path;
    private Stage stage; private MainView root; private MainController controller; private LectureFixture f;
    private long enrollment,assessment;
    GradeWorkflowCheck(Path path) { this.path=path; }
    void verify() throws Exception {
        f=LectureFixture.create(path);
        var ahmed=f.academic().students().create(new StudentInput("2026001","Ahmed","Ali","",""));
        var sara=f.academic().students().create(new StudentInput("2026002","Sara","Hassan","",""));
        enrollment=f.academic().enrollments().enroll(ahmed.id(),f.first().id()).id();
        f.academic().enrollments().enroll(sara.id(),f.first().id());
        try {
            open(); await(()->selector().getItems().size()==2);
            fx(()->{ assertTrue(button(root,"save-grades").isDisabled()); selector().getSelectionModel().selectFirst(); return null; });
            ready();
            fx(()->{ tabs().getSelectionModel().select(2); button(root,"new-category").fire(); return null; });
            await(()->!noDialog());
            fx(()->{ text(dialog(),"category-name","Assignments"); text(dialog(),"category-weight","100"); button(dialog(),"save-academic").fire(); return null; });
            ready();
            fx(()->{ tabs().getSelectionModel().select(1); button(root,"new-assessment").fire(); return null; });
            await(()->!noDialog());
            fx(()->{ text(dialog(),"assessment-title","Project"); text(dialog(),"assessment-maximum","20"); text(dialog(),"assessment-date","2026-11-01"); button(dialog(),"save-academic").fire(); return null; });
            ready(); assessment=f.academic().grades().load(f.first().id()).assessments().getFirst().id();
            fx(()->{ tabs().getSelectionModel().select(0); root.applyCss(); root.layout(); return null; });
            await(()->root.lookup(cell())!=null);
            fx(()->{ text(root,cell().substring(1),"18"); assertFalse(button(root,"save-grades").isDisabled()); button(root,"save-grades").fire(); return null; });
            ready();
            assertEquals("18.00",f.academic().grades().load(f.first().id()).grades().getFirst().score().toPlainString());
            fx(()->{ text(root,cell().substring(1),"99"); button(root,"save-grades").fire(); return null; });
            await(()->!button(root,"save-grades").isDisabled() && ((Label)root.lookup("#grade-status")).getText().contains("Score must"));
            assertEquals("18.00",f.academic().grades().load(f.first().id()).grades().getFirst().score().toPlainString());
            // Cancel navigation must preserve the invalid draft and the active context.
            Platform.runLater(()->((ToggleButton)root.lookup("#nav-courses")).fire()); await(()->!noDialog());
            fx(()->{ ((Button)dialog().lookupButton(ButtonType.CANCEL)).fire(); assertEquals("Grades",root.currentSection()); return null; });
            fx(()->{
                text(root,cell().substring(1),"17"); var field=root.lookup(cell());
                button(root,"sidebar-toggle").fire(); assertTrue(root.isSidebarCollapsed());
                assertSame(field,root.lookup(cell())); assertEquals("17",((TextField)field).getText());
                ((ToggleButton)root.lookup("#nav-grades")).fire();
                assertSame(field,root.lookup(cell())); assertTrue(noDialog());
                button(root,"sidebar-toggle").fire(); assertFalse(root.isSidebarCollapsed());
                root.themes().select(ThemeMode.DARK); root.applyCss(); root.layout();
                assertSame(field,root.lookup(cell())); assertEquals("17",((TextField)field).getText());
                assertFalse(button(root,"save-grades").isDisabled()); assertEquals("Grades",root.currentSection());
                ApplicationSmokeTest.saveSnapshot(root.snapshot(null,null),"theme-unsaved-gradebook-dark.png");
                root.themes().select(ThemeMode.LIGHT); return null;
            });
            Platform.runLater(()->selector().getSelectionModel().selectLast()); await(()->!noDialog());
            fx(()->{ ((Button)dialog().lookupButton(ButtonType.CANCEL)).fire(); return null; });
            await(()->selector().getValue().offering().id()==f.first().id());
            // Window close protection is shared with the real application's close request.
            Platform.runLater(()->stage.fireEvent(new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST))); await(()->!noDialog());
            fx(()->{ ((Button)dialog().lookupButton(ButtonType.CANCEL)).fire(); assertTrue(stage.isShowing()); button(root,"save-grades").fire(); return null; });
            ready();
            fx(()->{ button(root,"bulk-grades").fire(); return null; }); await(()->!noDialog());
            fx(()->{ text(dialog(),"grade-bulk-values","2026001\t18\n2026002\t0"); button(dialog(),"save-academic").fire(); return null; });
            await(this::noDialog);
            fx(()->{ assertFalse(button(root,"save-grades").isDisabled()); button(root,"save-grades").fire(); return null; }); ready();
            assertEquals(2,f.academic().grades().load(f.first().id()).grades().size());
            Path report=path.getParent().resolve("ui-gradebook.xlsx");
            fx(()->{ button(root,"export-grades-excel").fire(); return null; }); await(()->!noDialog());
            fx(()->{ dialog().applyCss(); dialog().layout(); ApplicationSmokeTest.saveSnapshot(dialog().snapshot(null,null),"excel-export-dialog.png"); return null; });
            fx(()->{ text(dialog(),"excel-export-path",report.toString()); button(dialog(),"save-academic").fire(); return null; }); await(this::noDialog);
            assertTrue(java.nio.file.Files.isRegularFile(report));
            try(var input=java.nio.file.Files.newInputStream(report);var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(input)) { assertEquals(4,workbook.getNumberOfSheets()); assertNotNull(workbook.getSheet("Attendance")); }
            fx(()->{
                text(root,"grade-search","sara"); assertEquals(1,table().getItems().size()); text(root,"grade-search","");
                table().getColumns().get(0).setSortType(TableColumn.SortType.DESCENDING);
                table().getSortOrder().setAll(List.of(table().getColumns().get(0))); table().sort();
                assertEquals("2026002",table().getItems().getFirst().student().studentNumber());
                root.applyCss(); root.layout(); ApplicationSmokeTest.saveSnapshot(root.snapshot(null,null),"gradebook.png");
                return null;
            });
            fx(()->{ stage.setWidth(1100); stage.setHeight(740); return null; });
            await(()->stage.getScene().getWidth()<1101);
            fx(()->{ root.applyCss(); root.layout(); ApplicationSmokeTest.saveSnapshot(root.snapshot(null,null),"gradebook-minimum.png"); return null; });
            fx(()->{ assertEquals("18.00",((TextField)root.lookup(cell())).getText()); text(root,cell().substring(1),"1"); return null; });
            fx(()->{ button(root,"export-grades-excel").fire(); assertTrue(noDialog()); assertTrue(((Label)root.lookup("#grade-status")).getText().contains("Save or discard")); return null; });
            Platform.runLater(()->button(root,"reload-grades").fire()); await(()->!noDialog());
            fx(()->{ button(dialog(),"confirm-academic-action").fire(); return null; }); ready();
            fx(()->{ assertEquals("18.00",((TextField)root.lookup(cell())).getText()); return null; });
            close(); open(); await(()->selector().getItems().size()==2);
            fx(()->{ selector().getSelectionModel().selectFirst(); return null; }); ready();
            await(()->root.lookup(cell())!=null);
            fx(()->{ assertEquals("18.00",((TextField)root.lookup(cell())).getText()); tabs().getSelectionModel().select(2); button(root,"grading-scale").fire(); return null; });
            await(()->!noDialog());
            fx(()->{ text(dialog(),"scale-a","85"); text(dialog(),"scale-pass","50"); button(dialog(),"save-academic").fire(); return null; }); ready();
            assertEquals("85.00",f.academic().grades().load(f.first().id()).scale().a().toPlainString());
            fx(()->{ tabs().getSelectionModel().select(1); assessments().getSelectionModel().selectFirst(); button(root,"edit-assessment").fire(); return null; });
            await(()->!noDialog());
            fx(()->{ text(dialog(),"assessment-title","Revised Project"); button(dialog(),"save-academic").fire(); return null; }); ready();
            fx(()->{ assessments().getSelectionModel().selectFirst(); return null; });
            Platform.runLater(()->button(root,"delete-assessment").fire()); await(()->!noDialog());
            fx(()->{ ((Button)dialog().lookupButton(ButtonType.CANCEL)).fire(); return null; });
            assertEquals(2,f.academic().grades().load(f.first().id()).grades().size());
            Platform.runLater(()->button(root,"delete-assessment").fire()); await(()->!noDialog());
            fx(()->{ button(dialog(),"confirm-academic-action").fire(); return null; }); ready();
            assertTrue(f.academic().grades().load(f.first().id()).grades().isEmpty());
        } finally { close(); }
    }
    private String cell() { return "#grade-cell-"+enrollment+"-"+assessment; }
    private void open() throws Exception {
        fx(()->{
            root=new MainView(); controller=new MainController(root,new CourseService(new CourseRepository(f.database())),AcademicServices.create(f.database()));
            stage=new Stage(); var scene=new Scene(root,1360,900); scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            stage.setScene(scene); stage.setOnCloseRequest(e->{ if(!controller.canClose()) { e.consume(); } }); stage.show();
            ((ToggleButton)root.lookup("#nav-grades")).fire(); return null;
        });
    }
    private void close() throws Exception { fx(()->{ if(stage!=null) { stage.setOnCloseRequest(null); stage.close(); } if(controller!=null) { controller.close(); } return null; }); }
    private void ready() throws Exception { await(()->noDialog() && !button(root,"new-category").isDisabled()); }
    @SuppressWarnings("unchecked") private ComboBox<OfferingSummary> selector() { return (ComboBox<OfferingSummary>)root.lookup("#active-offering"); }
    @SuppressWarnings("unchecked") private TableView<EnrolledStudent> table() { return (TableView<EnrolledStudent>)root.lookup("#gradebook-table"); }
    @SuppressWarnings("unchecked") private TableView<Assessment> assessments() { return (TableView<Assessment>)root.lookup("#grade-assessments"); }
    private TabPane tabs() { return (TabPane)root.lookup("#grade-tabs"); }
    private boolean noDialog() { return Window.getWindows().stream().noneMatch(w->w!=stage && w.isShowing()); }
    private DialogPane dialog() { return Window.getWindows().stream().filter(w->w!=stage && w.isShowing()).map(w->w.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).toList().getLast(); }
    private static Button button(Parent parent,String id) { return (Button)parent.lookup("#"+id); }
    private static void text(Parent parent,String id,String value) { ((TextInputControl)parent.lookup("#"+id)).setText(value); }
    private static <T> T fx(Callable<T> action) throws Exception { var task=new FutureTask<>(action); Platform.runLater(task); return task.get(10,TimeUnit.SECONDS); }
    private static void await(Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!fx(condition)) { if(System.nanoTime()>until) { fail("Timed out waiting for gradebook UI"); } Thread.sleep(25); }
    }
}
