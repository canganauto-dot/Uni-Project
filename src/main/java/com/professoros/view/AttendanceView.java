package com.professoros.view;
import com.professoros.model.*;
import com.professoros.service.*;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.util.*;
import java.util.function.BiConsumer;
public final class AttendanceView extends VBox {
    public final ComboBox<Lecture> lecture=new ComboBox<>();
    public final Button save=AcademicControls.button("Save attendance","save-attendance","primary-button"),reload=AcademicControls.button("Discard / Reload","reload-attendance","secondary-button"),history=AcademicControls.button("Student history","attendance-history","secondary-button");
    public final ComboBox<String> bulk=new ComboBox<>(FXCollections.observableArrayList("PRESENT","ABSENT","LATE","EXCUSED","Unmarked"));
    public final Button apply=AcademicControls.button("Mark roster","bulk-attendance","secondary-button");
    public final TableView<EnrolledStudent> table=AcademicControls.table("attendance-table");
    private final Label context=DashboardView.label("Select an offering to record attendance.","context-strip"),status=DashboardView.label("","muted"),summary=DashboardView.label("","statistics-strip");
    private final TextField search=new TextField();
    private AttendanceBook book;
    private AttendanceDraft draft;
    private BiConsumer<Long,AttendanceStatus> change=(id,value)->{};
    public AttendanceView() {
        setId("attendance-page");getStyleClass().addAll("page","feature-page");setSpacing(12);
        lecture.setId("attendance-lecture");lecture.setPrefWidth(480);lecture.setConverter(new StringConverter<>() {
            public String toString(Lecture l) { return l==null?"Choose lecture / date":l.plannedDate()+" · "+l.lectureNumber()+". "+l.title()+(l.status()==LectureStatus.CANCELLED?" · Cancelled":""); }
            public Lecture fromString(String s) { throw new UnsupportedOperationException("Selection only"); }
        });
        bulk.setId("attendance-bulk-status");bulk.setValue("PRESENT");
        search.setId("attendance-search");search.setPromptText("Search student number or name");search.textProperty().addListener((o,a,b)->filter());AcademicControls.searchShortcut(this,search);
        AcademicControls.column(table,"Student ID",s->s.student().studentNumber(),130);
        AcademicControls.column(table,"Student",s->s.student().fullName(),210);
        AcademicControls.column(table,"Enrollment",s->s.enrollment().status().toString(),120);
        var mark=new TableColumn<EnrolledStudent,String>("Attendance");mark.setPrefWidth(170);mark.setMinWidth(140);mark.setSortable(false);
        mark.setCellValueFactory(r->new javafx.beans.property.ReadOnlyStringWrapper(r.getValue().student().studentNumber()));
        mark.setCellFactory(c->new TableCell<>() {
            private final ComboBox<String> input=new ComboBox<>(FXCollections.observableArrayList("Unmarked","PRESENT","ABSENT","LATE","EXCUSED"));
            private boolean setting;
            { input.setMaxWidth(Double.MAX_VALUE);input.setOnAction(e->{if(!setting && getTableRow().getItem()!=null && draft!=null) change.accept(getTableRow().getItem().enrollment().id(),parse(input.getValue()));}); }
            protected void updateItem(String item,boolean empty) {
                super.updateItem(item,empty);setting=true;
                try {
                    if(empty || getTableRow()==null || getTableRow().getItem()==null || draft==null) {setGraphic(null);return;}
                    var student=getTableRow().getItem();var value=draft.value(student.enrollment().id());
                    input.setId("attendance-cell-"+student.enrollment().id());input.setAccessibleText(student.student().fullName()+" attendance");
                    input.setValue(value==null?"Unmarked":value.name());input.setDisable(lecture.getValue()==null || lecture.getValue().status()==LectureStatus.CANCELLED);setGraphic(input);
                } finally {setting=false;}
            }
        });table.getColumns().add(mark);
        AcademicControls.column(table,"Saved attendance %",s->{var value=AttendanceCalculator.student(book,s.enrollment().id()).percentage();return value==null?"Not yet rated":value+"%";},150);
        table.setPlaceholder(AcademicControls.empty("No enrolled students","Select an offering and lecture. Enroll students from the offering's Students page.",null));
        history.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        var policy=DashboardView.label(AttendanceCalculator.POLICY,"small-muted");policy.setWrapText(true);
        getChildren().addAll(AcademicControls.heading("Attendance","Mark enrolled students against the active offering's lectures.",save),context,
                AcademicControls.toolbar(AcademicControls.filter("Lecture / date",lecture),reload,history),search,
                AcademicControls.toolbar(AcademicControls.filter("Bulk status (entire roster, including hidden rows)",bulk),apply),table,summary,policy,status);
        VBox.setVgrow(table,Priority.ALWAYS);setBusy(true);
    }
    public static AttendanceStatus parse(String value) { return value==null || value.equals("Unmarked")?null:AttendanceStatus.valueOf(value); }
    public void context(String value) {context.setText(value);}
    public void load(AttendanceBook book) {this.book=book;lecture.getItems().setAll(book.lectures());summary.setText("Saved course summary · "+AttendanceCalculator.course(book).display()+" · Withdrawn excluded");filter();}
    public void draft(AttendanceDraft value) {draft=value;table.refresh();dirty();}
    public void onChange(BiConsumer<Long,AttendanceStatus> action) {change=action;}
    public void dirty() {save.setDisable(draft==null || !draft.dirty());status(lecture.getValue()!=null && lecture.getValue().status()==LectureStatus.CANCELLED?"Cancelled lecture: history is read-only and excluded from summaries.":draft!=null && draft.dirty()?"Unsaved changes. Save attendance to persist.":"Saved attendance. Unmarked is not Absent.",false);}
    public void status(String text,boolean error) {AcademicControls.status(status,text,error);}
    public void setBusy(boolean busy) {
        boolean readOnly=lecture.getValue()==null || lecture.getValue().status()==LectureStatus.CANCELLED;
        lecture.setDisable(busy);table.setDisable(busy);bulk.setDisable(busy || readOnly);apply.setDisable(busy || readOnly);
        reload.setDisable(busy);save.setDisable(busy || readOnly || draft==null || !draft.dirty());
    }
    public void clear() {book=null;draft=null;lecture.getItems().clear();table.getItems().clear();summary.setText("");status.setText("");setBusy(true);}
    public void showHistory() {
        var row=table.getSelectionModel().getSelectedItem();if(row==null || book==null)return;
        var text=new StringBuilder(AttendanceCalculator.student(book,row.enrollment().id()).display()+"\n\nSaved records only. Cancelled lectures excluded from percentage.\n");
        for(var item:book.lectures()) {
            var value=book.status(row.enrollment().id(),item.id());
            text.append(item.plannedDate()).append(" · ").append(item.title()).append(" · ").append(value==null?"Unmarked":value)
                    .append(item.status()==LectureStatus.CANCELLED?" (cancelled)":"").append('\n');
        }
        var area=new TextArea(text.toString());area.setEditable(false);area.setWrapText(true);area.setPrefSize(620,380);
        AcademicControls.details(getScene().getWindow(),"Attendance history · "+row.student().fullName(),area);
    }
    private void filter() {if(book==null)return;String q=search.getText().strip().toLowerCase(Locale.ROOT);table.getItems().setAll(book.students().stream().filter(s->(s.student().studentNumber()+" "+s.student().fullName()).toLowerCase(Locale.ROOT).contains(q)).toList());}
}
