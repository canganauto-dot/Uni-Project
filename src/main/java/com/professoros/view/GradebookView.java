package com.professoros.view;
import com.professoros.model.*;
import com.professoros.service.GradebookDraft;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.BiConsumer;

public final class GradebookView extends VBox {
    public final Button save=button("Save Grades","save-grades"),discard=button("Discard / Reload","reload-grades"),bulk=button("Bulk Entry","bulk-grades");
    public final Button exportExcel=button("Export to Excel","export-grades-excel");
    public final Button newCategory=button("New Category","new-category"),editCategory=button("Edit","edit-category"),deleteCategory=button("Delete","delete-category");
    public final Button newAssessment=button("New Assessment","new-assessment"),editAssessment=button("Edit","edit-assessment"),deleteAssessment=button("Delete","delete-assessment"),scale=button("Grading Scale","grading-scale");
    public final TableView<AssessmentCategory> categories=AcademicControls.table("grade-categories");
    public final TableView<Assessment> assessments=AcademicControls.table("grade-assessments");
    public final TableView<EnrolledStudent> table=AcademicControls.table("gradebook-table");
    public final TextField search=new TextField();
    private final Label context=DashboardView.label("Select a course offering to manage grades.","muted");
    private final Label status=DashboardView.label("","muted"),weights=DashboardView.label("","muted"),statistics=DashboardView.label("","muted");
    private final TabPane tabs=new TabPane();
    private GradebookDraft draft;
    private final ObservableList<EnrolledStudent> rows=FXCollections.observableArrayList();
    private final Map<Long,ReadOnlyStringWrapper> results=new HashMap<>();
    private final List<Button> buttons;
    private BiConsumer<GradeKey,String> changed=(k,v)->{};
    public GradebookView() {
        setId("grades-page"); getStyleClass().add("page"); setSpacing(12);
        search.setId("grade-search"); search.setPromptText("Search student number or name"); search.setPrefWidth(260);
        status.setId("grade-status"); weights.setId("grade-weights"); statistics.setId("grade-statistics");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        var filtered=new FilteredList<>(rows,s->true); var sorted=new SortedList<>(filtered); sorted.comparatorProperty().bind(table.comparatorProperty()); table.setItems(sorted);
        search.textProperty().addListener((o,a,b)->filtered.setPredicate(s->(s.student().studentNumber()+" "+s.student().firstName()+" "+s.student().lastName()).toLowerCase(Locale.ROOT).contains(b.strip().toLowerCase(Locale.ROOT))));
        table.setPlaceholder(AcademicControls.empty("No enrolled students match.","Enroll students in this offering, or clear the search.",null));
        AcademicControls.column(categories,"Category",AssessmentCategory::name,220); AcademicControls.column(categories,"Weight %",c->c.weight().toPlainString(),100);
        AcademicControls.column(assessments,"Assessment",Assessment::title,220); AcademicControls.column(assessments,"Maximum marks",a->a.maximumMarks().toPlainString(),110);
        AcademicControls.column(assessments,"Date",a->a.date().toString(),120);
        AcademicControls.column(assessments,"Category",a->draft==null?"":draft.book().categories().stream().filter(c->c.id()==a.categoryId()).map(AssessmentCategory::name).findFirst().orElse(""),150);
        categories.setPlaceholder(new Label("Create categories and assign weights (total 100%)."));
        assessments.setPlaceholder(new Label("Create a category, then add assessments."));
        tabs.setId("grade-tabs");
        tabs.getTabs().addAll(tab("Gradebook",new VBox(10,new FlowPane(8,8,search,save,discard,bulk,exportExcel),table)),
                tab("Assessments",new VBox(10,new FlowPane(8,8,newAssessment,editAssessment,deleteAssessment),assessments)),
                tab("Categories & Scale",new VBox(10,new FlowPane(8,8,newCategory,editCategory,deleteCategory,scale),categories)));
        VBox.setVgrow(tabs,Priority.ALWAYS);
        getChildren().addAll(DashboardView.label("Grades","page-title"),context,weights,tabs,statistics,status);
        buttons=List.of(save,discard,bulk,exportExcel,newCategory,editCategory,deleteCategory,newAssessment,editAssessment,deleteAssessment,scale);
        setBusy(true);
    }
    private static Button button(String name,String id) { return AcademicControls.button(name,id,id.equals("save-grades")?"primary-button":"secondary-button"); }
    private static Tab tab(String title,VBox body) { Tab tab=new Tab(title,body); tab.setClosable(false); return tab; }
    public void onChange(BiConsumer<GradeKey,String> action) { changed=action; }
    public void begin(String label) { context.setText(label); draft=null; rows.clear(); categories.getItems().clear(); assessments.getItems().clear(); table.getColumns().clear(); weights.setText(""); statistics.setText(""); setBusy(true); }
    public void load(GradebookDraft value) {
        draft=value; categories.getItems().setAll(value.book().categories()); assessments.getItems().setAll(value.book().assessments());
        rows.setAll(value.book().students()); table.getColumns().clear();
        AcademicControls.column(table,"Student number",s->s.student().studentNumber(),135);
        AcademicControls.column(table,"Student",s->s.student().firstName()+" "+s.student().lastName(),180);
        AcademicControls.column(table,"Enrollment",s->s.enrollment().status().toString(),115);
        for(var assessment:value.book().assessments()) {
            TableColumn<EnrolledStudent,String> column=new TableColumn<>(assessment.title()+" / "+assessment.maximumMarks());
            column.setPrefWidth(145); column.setMinWidth(120);
            column.setCellValueFactory(r->new ReadOnlyStringWrapper(draft.value(new GradeKey(r.getValue().enrollment().id(),assessment.id()))));
            column.setCellFactory(c->new TableCell<>() {
                private final TextField input=new TextField(); private boolean setting;
                {
                    input.setPromptText("Ungraded"); input.setMaxWidth(Double.MAX_VALUE);
                    input.textProperty().addListener((o,a,b)->{
                        if(!setting && getTableRow()!=null && getTableRow().getItem()!=null) {
                            var key=new GradeKey(getTableRow().getItem().enrollment().id(),assessment.id());
                            changed.accept(key,b);
                        }
                    });
                }
                @Override protected void updateItem(String item,boolean empty) {
                    super.updateItem(item,empty); setting=true;
                    try {
                        if(empty || getTableRow()==null || getTableRow().getItem()==null) { setGraphic(null); }
                        else { var key=new GradeKey(getTableRow().getItem().enrollment().id(),assessment.id()); input.setId("grade-cell-"+key.enrollmentId()+"-"+key.assessmentId()); input.setText(draft.value(key)); setGraphic(input); }
                    } finally { setting=false; }
                }
            });
            column.setComparator((a,b)->{ try { return new java.math.BigDecimal(a.isBlank()?"-1":a).compareTo(new java.math.BigDecimal(b.isBlank()?"-1":b)); } catch(NumberFormatException e) { return a.compareTo(b); } });
            table.getColumns().add(column);
        }
        TableColumn<EnrolledStudent,String> resultColumn=new TableColumn<>("Weighted /100 · Result");
        resultColumn.setPrefWidth(290);
        resultColumn.setComparator((a,b)->{
            try { return new java.math.BigDecimal(a.split(" ")[0]).compareTo(new java.math.BigDecimal(b.split(" ")[0])); }
            catch(NumberFormatException failure) { return a.compareTo(b); }
        });
        resultColumn.setCellValueFactory(r->results.computeIfAbsent(r.getValue().enrollment().id(),k->new ReadOnlyStringWrapper("")));
        table.getColumns().add(resultColumn);
        setBusy(false);
    }
    public void summaries(Map<Long,String> values,String weightText,String stats,boolean refresh) {
        results.forEach((id,property)->property.set(values.getOrDefault(id,"—")));
        values.forEach((id,value)->results.computeIfAbsent(id,k->new ReadOnlyStringWrapper()).set(value));
        weights.setText(weightText); statistics.setText(stats);
        // Refresh after load/bulk, not on every typed character (would destroy the focused editor).
        if(refresh) { table.refresh(); }
    }
    public void status(String message,boolean error) { AcademicControls.status(status,message,error); }
    public void setBusy(boolean busy) { buttons.forEach(b->b.setDisable(busy)); tabs.setDisable(busy); }
    public void dirty(boolean dirty) { save.setDisable(!dirty); }
}
