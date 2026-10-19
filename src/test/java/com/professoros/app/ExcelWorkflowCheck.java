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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

final class ExcelWorkflowCheck {
    private final Path directory;
    private Stage stage; private MainView root; private MainController controller;
    ExcelWorkflowCheck(Path directory) { this.directory=directory; }
    void verify() throws Exception {
        Files.createDirectories(directory); var f=LectureFixture.create(directory.resolve("import.db"));
        var file=directory.resolve("students.xlsx");
        try(var workbook=new XSSFWorkbook()) {
            var sheet=workbook.createSheet("Students");
            String[][] data={{"الرقم الجامعي","اسم الطالب","البريد الإلكتروني","الشعبة"},{"0001","Ahmed Ali","ahmed@example.edu","A"},{"0002","Sara Hassan","invalid","A"},{"0003","أحمد حسن","","B"}};
            for(int i=0;i<data.length;i++) { var row=sheet.createRow(i); for(int j=0;j<data[i].length;j++) { row.createCell(j).setCellValue(data[i][j]); } }
            try(var out=Files.newOutputStream(file)) { workbook.write(out); }
        }
        try {
            fx(()->{
                root=new MainView(); controller=new MainController(root,new CourseService(new CourseRepository(f.database())),f.academic());
                stage=new Stage(); var scene=new Scene(root,1360,900); scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm()); stage.setScene(scene); stage.show();
                ((ToggleButton)root.lookup("#nav-students")).fire(); return null;
            });
            await(()->!button(root,"import-students-excel").isDisabled());
            fx(()->{ button(root,"import-students-excel").fire(); return null; }); await(()->!noDialog());
            load(file);
            fx(()->{ button(dialog(),"preview-student-import").fire(); return null; });
            await(()->!button(dialog(),"confirm-student-import").isDisabled());
            fx(()->{
                assertEquals(3,rows().getItems().size()); assertEquals(StudentImportRow.Action.ERROR,rows().getItems().get(1).action());
                assertEquals("0001",rows().getItems().getFirst().input().studentNumber());
                rows().getSelectionModel().select(1);
                assertTrue(((Label)dialog().lookup("#student-import-detail")).getText().contains("email"));
                dialog().applyCss(); dialog().layout(); ApplicationSmokeTest.saveSnapshot(dialog().snapshot(null,null),"excel-import-preview.png");
                ((ComboBox<?>)dialog().lookup("#map-id")).getSelectionModel().selectFirst();
                assertTrue(button(dialog(),"confirm-student-import").isDisabled());
                ((ComboBox<?>)dialog().lookup("#map-id")).getSelectionModel().select(1);
                button(dialog(),"preview-student-import").fire(); return null;
            });
            await(()->!button(dialog(),"confirm-student-import").isDisabled());
            fx(()->{ button(dialog(),"confirm-student-import").fire(); return null; });
            await(()->status().contains("Import committed"));
            fx(()->{ assertTrue(status().contains("Created: 2")); assertTrue(status().contains("Failed validation: 1")); closeDialog(); return null; });
            assertEquals(2,f.academic().students().findAll().size());
            await(()->selector().getItems().size()==2);
            fx(()->{ selector().getSelectionModel().selectFirst(); return null; });
            await(()->root.lookup("#offering-details-page")!=null);
            fx(()->{ ((com.professoros.view.OfferingDetailsView)root.lookup("#offering-details-page")).tabs.getSelectionModel().select(1); return null; });
            await(()->root.lookup("#import-enrollments-excel")!=null && !button(root,"import-enrollments-excel").isDisabled());
            fx(()->{ button(root,"import-enrollments-excel").fire(); return null; }); await(()->!noDialog()); load(file);
            fx(()->{ button(dialog(),"preview-student-import").fire(); return null; });
            await(()->!button(dialog(),"confirm-student-import").isDisabled());
            fx(()->{ assertEquals(StudentImportRow.Action.ENROLL,rows().getItems().getFirst().action()); button(dialog(),"confirm-student-import").fire(); return null; });
            await(()->status().contains("Import committed"));
            fx(()->{ assertTrue(status().contains("Enrolled: 1")); assertTrue(status().contains("Failed validation: 2")); closeDialog(); return null; });
            var reopened=AcademicServices.create(new com.professoros.database.Database(directory.resolve("import.db")));
            assertEquals(2,reopened.students().findAll().size());
            assertEquals("0001",reopened.enrollments().findStudentsForOffering(f.first().id()).getFirst().student().studentNumber());
        } finally {
            fx(()->{ for(Window w:List.copyOf(Window.getWindows())) { if(w instanceof Stage s) { s.close(); } } if(controller!=null) { controller.close(); } return null; });
        }
    }
    private void load(Path file) throws Exception {
        fx(()->{ ((TextField)dialog().lookup("#student-import-file")).setText(file.toString()); button(dialog(),"load-student-workbook").fire(); return null; });
        await(()->!button(dialog(),"preview-student-import").isDisabled());
    }
    private void closeDialog() { ((Button)dialog().lookupButton(ButtonType.CLOSE)).fire(); }
    private String status() { return ((Label)dialog().lookup("#student-import-status")).getText(); }
    @SuppressWarnings("unchecked") private TableView<StudentImportRow> rows() { return (TableView<StudentImportRow>)dialog().lookup("#student-import-rows"); }
    @SuppressWarnings("unchecked") private ComboBox<OfferingSummary> selector() { return (ComboBox<OfferingSummary>)root.lookup("#active-offering"); }
    private boolean noDialog() { return Window.getWindows().stream().noneMatch(w->w!=stage && w.isShowing()); }
    private DialogPane dialog() { return Window.getWindows().stream().filter(w->w!=stage && w.isShowing()).map(w->w.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).toList().getLast(); }
    private static Button button(Parent p,String id) { return (Button)p.lookup("#"+id); }
    private static <T> T fx(Callable<T> action) throws Exception { var task=new FutureTask<>(action); Platform.runLater(task); return task.get(10,TimeUnit.SECONDS); }
    private static void await(Callable<Boolean> condition) throws Exception { long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15); while(!fx(condition)) { if(System.nanoTime()>until) { fail("Timed out waiting for Excel UI"); } Thread.sleep(25); } }
}
