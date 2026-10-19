package com.professoros.app;
import com.professoros.controller.MainController;

import com.professoros.model.ThemeMode;
import com.professoros.repository.CourseRepository;
import com.professoros.service.*;
import com.professoros.support.LectureFixture;
import com.professoros.view.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

final class ThemeWorkflowCheck {
    static void verify(Path directory) throws Exception {
        var fixture=LectureFixture.create(directory.resolve("theme.db"));
        var today=java.time.LocalDate.now();
        fixture.academic().lectures().create(fixture.first().id(),LectureFixture.input(1,today.minusDays(2),com.professoros.model.LectureStatus.COMPLETED));
        fixture.academic().lectures().create(fixture.first().id(),LectureFixture.input(2,today,com.professoros.model.LectureStatus.PARTIALLY_COMPLETED));
        fixture.academic().lectures().create(fixture.first().id(),LectureFixture.input(3,today.plusDays(3),com.professoros.model.LectureStatus.PLANNED));
        var student=fixture.academic().students().create(new com.professoros.model.StudentInput("001","Ahmed","Ali","",""));
        fixture.academic().enrollments().enroll(student.id(),fixture.first().id());
        var preference=new ThemePreferences(directory.resolve("appearance.properties"));
        var task=new FutureTask<Void>(()->{
            var system=new javafx.beans.property.SimpleObjectProperty<>(javafx.application.ColorScheme.LIGHT);
            var probe=new VBox();
            try(var theme=new com.professoros.theme.ThemeManager(probe,new ThemePreferences(directory.resolve("probe.properties")),system)) {
                assertTrue(probe.getStyleClass().contains("theme-light")); system.set(javafx.application.ColorScheme.DARK);
                assertTrue(probe.getStyleClass().contains("theme-dark")); theme.select(ThemeMode.LIGHT);
                system.set(javafx.application.ColorScheme.LIGHT); system.set(javafx.application.ColorScheme.DARK);
                assertTrue(probe.getStyleClass().contains("theme-light")); theme.select(ThemeMode.SYSTEM);
                assertTrue(probe.getStyleClass().contains("theme-dark"));
            }
            MainView root=new MainView(preference);
            var controller=new MainController(root,new CourseService(new CourseRepository(fixture.database())),fixture.academic());
            var stage=new Stage(); stage.setScene(new Scene(root,1360,900)); stage.show();
            try {
                for(var mode:new ThemeMode[]{ThemeMode.LIGHT,ThemeMode.DARK}) {
                    root.themes().select(mode); capture(root,"theme-dashboard-"+mode.name().toLowerCase()+".png");
                    root.resize(1100,740); capture(root,"theme-dashboard-minimum-"+mode.name().toLowerCase()+".png");
                    assertTrue(root.lookup("#theme-toggle").localToScene(root.lookup("#theme-toggle").getBoundsInLocal()).getMaxX()<=1100);
                    root.resize(1360,900);
                }
                ((ToggleButton)root.lookup("#nav-settings")).fire(); root.applyCss(); root.layout();
                var settings=(ComboBox<?>)root.lookup("#appearance-mode"); assertEquals(ThemeMode.DARK,settings.getValue());
                var page=root.lookup("#terms-page");
                ((Button)root.lookup("#theme-toggle")).fire(); assertEquals(ThemeMode.SYSTEM,settings.getValue());
                assertSame(page,root.lookup("#terms-page")); assertEquals("Settings",root.currentSection());
                for(var mode:new ThemeMode[]{ThemeMode.LIGHT,ThemeMode.DARK}) {
                    root.themes().select(mode); capture(root,"theme-settings-"+mode.name().toLowerCase()+".png");
                }
                var dialog=new CourseFormDialog(stage,null); dialog.show();
                var field=(TextField)dialog.getDialogPane().lookup("#course-name"); field.setText("Unsaved teaching plan"); field.positionCaret(7);
                for(var mode:new ThemeMode[]{ThemeMode.LIGHT,ThemeMode.DARK}) {
                    root.themes().select(mode); assertEquals("Unsaved teaching plan",field.getText()); assertEquals(7,field.getCaretPosition());
                    assertTrue(dialog.getDialogPane().getScene().getRoot().getStyleClass().contains("theme-"+mode.name().toLowerCase()));
                    capture(dialog.getDialogPane(),"theme-dialog-"+mode.name().toLowerCase()+".png");
                }
                root.setSidebarCollapsed(true);
                assertSame(field,dialog.getDialogPane().lookup("#course-name"));
                assertEquals("Unsaved teaching plan",field.getText()); assertEquals(7,field.getCaretPosition());
                root.setSidebarCollapsed(false);
                dialog.close();
                var tip=new Tooltip("Appearance follows your saved preference"); tip.show(stage,stage.getX()+350,stage.getY()+100);
                root.themes().select(ThemeMode.LIGHT); root.themes().select(ThemeMode.DARK);
                assertTrue(tip.getScene().getRoot().getStyleClass().contains("theme-dark"));
                capture(tip.getScene().getRoot(),"theme-tooltip-dark.png");
                assertTrue(tip.getStyleClass().contains("theme-dark"));
                assertTrue(tip.getScene().getRoot().lookupAll(".tooltip").stream()
                        .filter(javafx.scene.layout.Region.class::isInstance).map(javafx.scene.layout.Region.class::cast)
                        .anyMatch(node->node.getBackground()!=null && node.getBackground().getFills().stream()
                                .anyMatch(fill->javafx.scene.paint.Color.web("#22232b").equals(fill.getFill())))); tip.hide();
                // Component specimen only; no chart/analytics feature added to the application.
                var x=new CategoryAxis(); var y=new NumberAxis(); var chart=new BarChart<String,Number>(x,y);
                var series=new XYChart.Series<String,Number>(); series.setName("Theme specimen"); series.getData().add(new XYChart.Data<>("A",4)); series.getData().add(new XYChart.Data<>("B",7)); chart.getData().add(series); chart.setAnimated(false);
                var sample=new VBox(12,new Label("Component specimen"),new TextField("Keyboard focus"),new CheckBox("Option"),chart);
                var specimen=new Stage(); specimen.initOwner(stage); specimen.setScene(new Scene(sample,600,450)); specimen.show();
                for(var mode:new ThemeMode[]{ThemeMode.LIGHT,ThemeMode.DARK}) { root.themes().select(mode); capture(sample,"theme-components-"+mode.name().toLowerCase()+".png"); }
                specimen.close();
                assertEquals(ThemeMode.DARK,new ThemePreferences(directory.resolve("appearance.properties")).load());
                root.themes().select(ThemeMode.SYSTEM);
                assertEquals(ThemeMode.SYSTEM,root.themes().mode());
            } finally { stage.close(); controller.close(); }
            return null;
        }); Platform.runLater(task); task.get(30,TimeUnit.SECONDS);
        // Capture Settings after its asynchronous academic-term load has completed.
        MainView[] view=new MainView[1]; MainController[] control=new MainController[1]; Stage[] stage=new Stage[1];
        fx(()->{
            view[0]=new MainView(preference); control[0]=new MainController(view[0],new CourseService(new CourseRepository(fixture.database())),fixture.academic());
            stage[0]=new Stage(); stage[0].setScene(new Scene(view[0],1360,900)); stage[0].show();
            ((ToggleButton)view[0].lookup("#nav-settings")).fire(); return null;
        });
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(fx(()->view[0].lookup("#new-term").isDisabled())) { if(System.nanoTime()>deadline) { fail("Settings did not finish loading"); } Thread.sleep(25); }
            fx(()->{ for(var mode:new ThemeMode[]{ThemeMode.LIGHT,ThemeMode.DARK}) { view[0].themes().select(mode); capture(view[0],"theme-settings-"+mode.name().toLowerCase()+".png"); } return null; });
            fx(()->{
                @SuppressWarnings("unchecked") var offering=(ComboBox<com.professoros.model.OfferingSummary>)view[0].lookup("#active-offering");
                offering.getSelectionModel().selectFirst();
                ((ToggleButton)view[0].lookup("#nav-dashboard")).fire(); return null;
            });
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(fx(()->view[0].lookup("#refresh-dashboard").isDisabled())) { if(System.nanoTime()>deadline) fail("Dashboard did not load"); Thread.sleep(25); }
            fx(()->{
                assertEquals("1",((Label)view[0].lookup("#dashboard-enrollment-count")).getText());
                var page=view[0].lookup("#dashboard"); var nav=view[0].lookup("#nav-dashboard");
                for(var mode:new ThemeMode[]{ThemeMode.LIGHT,ThemeMode.DARK}) {
                    view[0].themes().select(mode);
                    view[0].resize(1360,900); capture(view[0],"shell-dashboard-"+mode.name().toLowerCase()+".png");
                    view[0].resize(1100,740); capture(view[0],"shell-minimum-"+mode.name().toLowerCase()+".png");
                    assertTrue(view[0].lookup("#theme-toggle").localToScene(view[0].lookup("#theme-toggle").getBoundsInLocal()).getMaxX()<=1100);
                    ((Button)view[0].lookup("#sidebar-toggle")).fire();
                    assertTrue(view[0].isSidebarCollapsed()); assertSame(page,view[0].lookup("#dashboard"));
                    assertSame(nav,view[0].lookup("#nav-dashboard")); assertTrue(((ToggleButton)nav).isSelected());
                    assertEquals("Dashboard",nav.getAccessibleText());
                    capture(view[0],"shell-collapsed-"+mode.name().toLowerCase()+".png");
                    ((Button)view[0].lookup("#sidebar-toggle")).fire();
                }
                return null;
            });
        } finally { fx(()->{ stage[0].close(); control[0].close(); return null; }); }
    }
    private static <T> T fx(Callable<T> action) throws Exception { var task=new FutureTask<>(action); Platform.runLater(task); return task.get(15,TimeUnit.SECONDS); }
    private static void capture(Parent root,String name) throws Exception { root.applyCss(); root.layout(); ApplicationSmokeTest.saveSnapshot(root.snapshot(null,null),name); }
}
