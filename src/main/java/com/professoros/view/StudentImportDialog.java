package com.professoros.view;
import com.professoros.excel.*;
import com.professoros.model.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.Path;
import java.util.*;

public final class StudentImportDialog extends Dialog<Void> {
    public final TextField file=new TextField();
    public final Button load=AcademicControls.button("Load Workbook","load-student-workbook","secondary-button");
    public final Button preview=AcademicControls.button("Preview Rows","preview-student-import","primary-button");
    public final Button confirm=AcademicControls.button("Confirm Valid Rows","confirm-student-import","primary-button");
    public final ComboBox<StudentWorkbook.Sheet> sheet=new ComboBox<>();
    public final Spinner<Integer> header=new Spinner<>(1,10001,1);
    private final Label status=DashboardView.label("Choose an .xlsx file. Nothing is saved until you confirm the preview.","muted");
    private final TableView<StudentImportRow> rows=AcademicControls.table("student-import-rows");
    private final Label detail=DashboardView.label("Select a preview row to read its full validation details.","muted");
    private final Map<StudentColumns.Field,ComboBox<Column>> columns=new EnumMap<>(StudentColumns.Field.class);
    private final VBox body=new VBox(10);
    private final Button browse=AcademicControls.button("Browse…","browse-student-workbook","secondary-button");
    private final GridPane mapping=new GridPane();
    private boolean busy,updating,loaded,reviewed;
    private Runnable invalidated=()->{};
    private record Column(int index,String label) { @Override public String toString() { return label; } }
    public StudentImportDialog(Window owner,String destination) {
        initOwner(owner); setTitle("Import Students from Excel"); setHeaderText(destination+" · Existing students are never overwritten");
        CourseDialogs.style(getDialogPane()); getDialogPane().getButtonTypes().add(ButtonType.CLOSE); getDialogPane().setPrefWidth(1100); setResizable(true);
        file.setId("student-import-file"); file.setPromptText("Select an .xlsx workbook"); HBox.setHgrow(file,Priority.ALWAYS);
        sheet.setId("student-import-sheet"); sheet.setPrefWidth(300); header.setId("student-import-header"); header.setPrefWidth(90);
        status.setId("student-import-status");
        browse.setOnAction(e->{ var picker=new FileChooser(); picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook","*.xlsx")); var chosen=picker.showOpenDialog(owner); if(chosen!=null) { file.setText(chosen.getAbsolutePath()); } });
        mapping.setHgap(12); mapping.setVgap(8); int i=0;
        for(var field:StudentColumns.Field.values()) {
            var combo=new ComboBox<Column>(); combo.setId("map-"+field.name().toLowerCase(Locale.ROOT)); combo.setPrefWidth(245);
            columns.put(field,combo); mapping.add(new Label(field.toString()),(i%2)*2,i/2); mapping.add(combo,(i%2)*2+1,i/2); i++;
            combo.valueProperty().addListener((o,a,b)->{ if(!updating) { invalidate(); } });
        }
        AcademicControls.column(rows,"Row",r->Integer.toString(r.rowNumber()),55);
        AcademicControls.column(rows,"Student ID",r->r.input().studentNumber(),110);
        AcademicControls.column(rows,"First name",r->r.input().firstName(),120);
        AcademicControls.column(rows,"Last name",r->r.input().lastName(),150);
        AcademicControls.column(rows,"Email",r->r.input().email(),180);
        AcademicControls.column(rows,"Section",StudentImportRow::section,80);
        AcademicControls.column(rows,"Action",r->r.action().toString(),90);
        AcademicControls.column(rows,"Details",StudentImportRow::message,420);
        rows.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY); rows.setPrefHeight(220);
        detail.setId("student-import-detail"); detail.setMaxWidth(1000);
        rows.getSelectionModel().selectedItemProperty().addListener((o,a,b)->AcademicControls.status(detail,b==null?"Select a preview row to read its full validation details.":"Row "+b.rowNumber()+": "+b.message(),b!=null && b.action()==StudentImportRow.Action.ERROR));
        rows.setPlaceholder(new Label("Load a workbook, check the column mapping, then preview rows."));
        body.getChildren().addAll(new HBox(8,file,browse,load),new HBox(10,new Label("Sheet"),sheet,new Label("Header row"),header),mapping,
                DashboardView.label("Map ID and full name OR first/last name. Full names split at the first space. Arabic digits normalize to 0–9. Formula cells are rejected.","small-muted"),
                DashboardView.label("IDs: set the Excel column to Text before entering IDs. For existing numbers, re-enter the original ID after formatting; lost zeros or digits cannot be restored automatically. Review every Numeric ID warning.","small-muted"),
                new HBox(10,preview,confirm),rows,detail,status);
        getDialogPane().setContent(body);
        file.textProperty().addListener((o,a,b)->{ loaded=false; invalidate(); });
        sheet.valueProperty().addListener((o,a,b)->{ if(!updating && b!=null) { updating=true; header.getValueFactory().setValue(StudentColumns.headerRow(b)); updating=false; autoMap(); } });
        header.valueProperty().addListener((o,a,b)->{ if(!updating && sheet.getValue()!=null) { autoMap(); } });
        setOnCloseRequest(e->{ if(busy) { e.consume(); } }); actions();
    }
    public Path path() { try { return Path.of(file.getText().strip()); } catch(java.nio.file.InvalidPathException e) { throw new DomainValidationException("Choose a valid workbook path."); } }
    public void onInvalidated(Runnable action) { invalidated=action; }
    public void loaded(StudentWorkbook workbook) {
        updating=true; sheet.getItems().setAll(workbook.sheets()); sheet.getSelectionModel().selectFirst();
        header.getValueFactory().setValue(StudentColumns.headerRow(sheet.getValue())); updating=false; loaded=true; autoMap();
    }
    private void autoMap() {
        updating=true;
        var row=sheet.getValue().rows().stream().filter(r->r.number()==header.getValue()).findFirst().orElse(new StudentWorkbook.Row(header.getValue(),List.of()));
        var detected=StudentColumns.detect(row); List<Column> choices=new ArrayList<>(); choices.add(new Column(-1,"Not mapped"));
        for(int i=0;i<row.cells().size();i++) { choices.add(new Column(i,(i+1)+" · "+row.cell(i).text())); }
        for(var field:StudentColumns.Field.values()) {
            var combo=columns.get(field); combo.getItems().setAll(choices); int selected=detected.getOrDefault(field,-1);
            combo.setValue(choices.stream().filter(c->c.index()==selected).findFirst().orElse(choices.getFirst()));
        }
        updating=false; invalidate();
    }
    public Map<StudentColumns.Field,Integer> mapping() {
        var result=new EnumMap<StudentColumns.Field,Integer>(StudentColumns.Field.class);
        columns.forEach((f,c)->{ if(c.getValue()!=null && c.getValue().index()>=0) { result.put(f,c.getValue().index()); } }); return result;
    }
    private void invalidate() { reviewed=false; rows.getItems().clear(); invalidated.run(); status("Generate a preview for the current sheet and mapping.",false); actions(); }
    public void showPreview(StudentImportPreview value) {
        rows.getItems().setAll(value.rows()); reviewed=value.actionable()>0;
        long errors=value.rows().stream().filter(r->r.action()==StudentImportRow.Action.ERROR).count(),skipped=value.rows().stream().filter(r->r.action()==StudentImportRow.Action.SKIP).count();
        status(value.actionable()+" actionable rows · "+skipped+" skipped · "+errors+" failed validation. Confirm imports only valid rows.",errors>0); actions();
    }
    public void finished(StudentImportResult result) { reviewed=false; status(result+". Import committed.",false); actions(); }
    public void clearReview() { reviewed=false; invalidated.run(); actions(); }
    public void beginLoad() { loaded=false; clearReview(); rows.getItems().clear(); }
    public void status(String value,boolean error) { AcademicControls.status(status,value,error); }
    public void setBusy(boolean value) { busy=value; actions(); }
    private void actions() {
        file.setDisable(busy); browse.setDisable(busy); load.setDisable(busy); sheet.setDisable(busy || !loaded); header.setDisable(busy || !loaded); mapping.setDisable(busy || !loaded);
        preview.setDisable(busy || !loaded); confirm.setDisable(busy || !reviewed); getDialogPane().lookupButton(ButtonType.CLOSE).setDisable(busy);
    }
}
