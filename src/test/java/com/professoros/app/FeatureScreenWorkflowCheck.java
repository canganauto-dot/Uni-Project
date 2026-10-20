package com.professoros.app;

import com.professoros.controller.MainController;
import com.professoros.model.*;
import com.professoros.repository.CourseRepository;
import com.professoros.service.*;
import com.professoros.support.LectureFixture;
import com.professoros.view.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Populated presentation regressions; existing workflows continue to exercise persistence and dialogs. */
final class FeatureScreenWorkflowCheck {
    static void verify(Path directory) throws Exception {
        var f=LectureFixture.create(directory.resolve("features.db")); var a=f.academic(); long offering=f.first().id();
        for(int i=1;i<=3;i++) {
            var student=a.students().create(new StudentInput("202600"+i,i==1?"Ahmed":i==2?"Sara":"Omar",i==2?"Hassan":"Ali","student"+i+"@example.edu",""));
            a.enrollments().enroll(student.id(),offering);
            a.lectures().create(offering,new LectureInput(i,List.of("Objects and responsibilities","Inheritance and composition","Interfaces in practice").get(i-1),LocalDate.now().plusDays(i),"Object-oriented design","",60,i==1?LectureStatus.COMPLETED:LectureStatus.PLANNED,""));
        }
        var questions=new ArrayList<Question>();
        for(int i=0;i<5;i++) questions.add(a.questions().create(new QuestionInput(f.course().id(),QuestionType.values()[i],
                List.of("Which statement best describes encapsulation?","An interface can define a contract without implementation.","Explain the difference between a class and an object.","Compare inheritance with composition using examples.","Implement a class that models a university course.").get(i),
                "Object-oriented design",Difficulty.values()[i%3],BigDecimal.valueOf(i+1),i==1?"TRUE":"Model answer","Explanation","",
                i==0?List.of(new AnswerChoice("Hiding internal representation",true),new AnswerChoice("Duplicating every field",false)):List.of())));
        var exam=a.exams().save(offering,null,new ExamInput("Object-oriented programming · Midterm",LocalDate.now().plusDays(10),90,new BigDecimal("15"),"Answer all questions. Show your reasoning."),questions.stream().map(Question::id).toList());
        var category=a.grades().saveCategory(offering,null,"Assignments",new BigDecimal("100"));
        var assessment=a.grades().saveAssessment(offering,null,category.id(),"Design exercise",new BigDecimal("20"),LocalDate.now());
        a.grades().saveAssessment(offering,null,category.id(),"Programming project",new BigDecimal("30"),LocalDate.now().plusDays(7));
        var enrollment=a.enrollments().findStudentsForOffering(offering).getFirst().enrollment();
        a.grades().saveGrades(offering,Map.of(new GradeKey(enrollment.id(),assessment.id()),"0"));
        MainView[] view=new MainView[1]; MainController[] controller=new MainController[1]; Stage[] stage=new Stage[1];
        fx(()->{
            view[0]=new MainView(new ThemePreferences(directory.resolve("appearance.properties")));
            controller[0]=new MainController(view[0],new CourseService(new CourseRepository(f.database())),a);
            stage[0]=new Stage(); stage[0].setScene(new Scene(view[0],1360,900)); stage[0].show(); return null;
        });
        try {
            await(()->((ComboBox<?>)view[0].lookup("#active-offering")).getItems().size()==2);
            fx(()->{ ((ComboBox<?>)view[0].lookup("#active-offering")).getSelectionModel().selectFirst(); return null; });
            String[][] screens={{"Courses","courses-table","new-course","course-search"},{"Students","students-table","new-student","student-search"},
                    {"Lectures","lectures-table","new-lecture","lecture-search"},{"Exam Bank","questions-table","new-question","question-search"},
                    {"Exams","exams-table","new-exam",""},{"Grades","gradebook-table","bulk-grades","grade-search"}};
            for(String[] screen:screens) {
                fx(()->{ ((ToggleButton)view[0].lookup("#nav-"+screen[0].toLowerCase(Locale.ROOT).replace(' ','-'))).fire(); return null; });
                await(()->view[0].lookup("#"+screen[2])!=null && !view[0].lookup("#"+screen[2]).isDisabled()
                        && !((TableView<?>)view[0].lookup("#"+screen[1])).getItems().isEmpty());
                fx(()->{
                    var table=(TableView<?>)view[0].lookup("#"+screen[1]); var items=List.copyOf(table.getItems());
                    for(var mode:List.of(ThemeMode.LIGHT,ThemeMode.DARK)) {
                        view[0].themes().select(mode);
                        for(int width:new int[]{1360,1100}) {
                            view[0].resize(width,width==1360?900:740); capture(view[0],screen[1]+"-"+mode.name().toLowerCase()+"-"+width);
                            assertSame(table,view[0].lookup("#"+screen[1])); assertEquals(items,table.getItems());
                            assertTrue(table.getWidth()>500); assertTrue(table.getHeight()>=175);
                            assertTrue(view[0].lookup("#"+screen[2]).localToScene(view[0].lookup("#"+screen[2]).getBoundsInLocal()).getMaxX()<=width,screen[0]+" primary action clipped");
                        }
                    }
                    return null;
                });
                if(!screen[3].isEmpty()) {
                    fx(()->{ table(view[0],screen[1]).requestFocus(); table(view[0],screen[1]).fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F,false,true,false,false)); return null; });
                    fx(()->{ assertSame(view[0].lookup("#"+screen[3]),view[0].getScene().getFocusOwner()); return null; });
                }
            }
            fx(()->{
                var zero=(TextField)view[0].lookup("#grade-cell-"+enrollment.id()+"-"+assessment.id());
                assertEquals("0.00",zero.getText()); assertTrue(zero.getAccessibleText().contains("Design exercise"));
                zero.setText("17"); assertEquals("Unsaved changes",((Label)view[0].lookup("#grade-draft-state")).getText());
                view[0].themes().select(ThemeMode.LIGHT); view[0].setSidebarCollapsed(true);
                assertSame(zero,view[0].lookup("#"+zero.getId())); assertEquals("17",zero.getText());
                assertEquals("0.00",a.grades().load(offering).grades().getFirst().score().toPlainString());
                view[0].setSidebarCollapsed(false); view[0].resize(1360,900);
                Dialog<?>[] forms={new CourseFormDialog(stage[0],f.course()),new StudentFormDialog(stage[0],a.students().findAll().getFirst()),
                        new LectureFormDialog(stage[0],"CS201 · Fall · Section A",a.lectures().overview(offering).lectures().getFirst()),
                        new QuestionFormDialog(stage[0],List.of(f.course()),f.course(),questions.getFirst())};
                String[] fields={"course-name","student-first","lecture-title","question-topic"};
                for(int i=0;i<forms.length;i++) {
                    var dialog=forms[i]; dialog.show();
                    var field=(TextField)dialog.getDialogPane().lookup("#"+fields[i]); field.setText("Unsaved revision");
                    for(var mode:List.of(ThemeMode.LIGHT,ThemeMode.DARK)) {
                        view[0].themes().select(mode); capture(dialog.getDialogPane(),"form-"+fields[i]+"-"+mode.name().toLowerCase());
                        assertEquals("Unsaved revision",field.getText()); assertSame(field,dialog.getDialogPane().lookup("#"+fields[i]));
                    }
                    dialog.close();
                }
                return null;
            });
            ExamBuilderDialog builder=fx(()->new ExamBuilderDialog(stage[0],"CS201 · Fall 2026 · Section A",questions,exam));
            fx(()->{ builder.show();return null; });
                var tabs=fx(()->(TabPane)builder.getDialogPane().lookup("#exam-builder-tabs"));
                for(var mode:List.of(ThemeMode.LIGHT,ThemeMode.DARK)) {
                    fx(()->{ view[0].themes().select(mode);return null; });
                    for(int tab=0;tab<2;tab++) {
                        int index=tab; fx(()->{tabs.getSelectionModel().select(index);return null;});
                        Thread.sleep(100); // Allow the tab skin's next pulse before snapshot/visibility assertions.
                        fx(()->{capture(builder.getDialogPane(),"builder-"+index+"-"+mode.name().toLowerCase());
                            var action=builder.getDialogPane().lookup(index==0?"#add-exam-question":"#exam-question-up");
                            var bounds=action.localToScene(action.getBoundsInLocal());
                            assertTrue(bounds.getMaxY()<builder.getDialogPane().lookup("#exam-mark-totals").localToScene(builder.getDialogPane().lookup("#exam-mark-totals").getBoundsInLocal()).getMinY());
                            if(index==1) assertTrue(table(builder.getDialogPane(),"exam-selected-questions").getColumns().getFirst().getWidth()>=55);
                            assertEquals(exam.questions().stream().map(ExamQuestion::questionId).toList(),builder.questionIds());
                            return null;});
                    }
                }
            fx(()->{
                assertTrue(((Label)builder.getDialogPane().lookup("#exam-mark-totals")).getText().contains("Calculated 15"));
                builder.close(); return null;
            });
        } finally { fx(()->{ controller[0].close(); stage[0].close(); return null; }); }
    }
    private static TableView<?> table(Parent root,String id) { return (TableView<?>)root.lookup("#"+id); }
    private static void capture(Parent root,String name) throws Exception { root.applyCss();root.layout();ApplicationSmokeTest.saveSnapshot(root.snapshot(null,null),"feature-"+name+".png"); }
    private static <T>T fx(Callable<T> action) throws Exception { var task=new FutureTask<>(action);Platform.runLater(task);return task.get(30,TimeUnit.SECONDS); }
    private static void await(Callable<Boolean> condition) throws Exception { long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!fx(condition)) { if(System.nanoTime()>end) fail("Feature screen did not finish loading");Thread.sleep(25); } }
}
