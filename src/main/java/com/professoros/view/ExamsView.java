package com.professoros.view;

import com.professoros.model.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;

public final class ExamsView extends VBox {
    public final TableView<Exam> table=AcademicControls.table("exams-table");
    public final Button create=AcademicControls.button("+ New Exam","new-exam","primary-button");
    public final Button edit=AcademicControls.button("Build / Edit","edit-exam","secondary-button");
    public final Button details=AcademicControls.button("View Details","exam-details","secondary-button");
    public final Button delete=AcademicControls.button("Delete Draft","delete-exam","danger-button");
    public final Button finalizeExam=AcademicControls.button("Finalize","finalize-exam","secondary-button");
    public final Button archive=AcademicControls.button("Archive","archive-exam","secondary-button");
    public final Button refresh=AcademicControls.button("Refresh","refresh-exams","secondary-button");
    public final Label message=DashboardView.label("","muted");
    private final Label context=DashboardView.label("","muted");
    private boolean hasOffering,busy;
    public ExamsView() {
        super(16); setId("exams-page"); getStyleClass().add("page");
        Region space=new Region(); HBox.setHgrow(space,Priority.ALWAYS);
        AcademicControls.column(table,"Exam",Exam::title,260); AcademicControls.column(table,"Date",e -> e.date().toString(),110);
        AcademicControls.column(table,"Status",e -> e.status().toString(),110);
        AcademicControls.column(table,"Marks",e -> e.calculatedMarks()+" / "+e.intendedMarks()+(e.marksDiffer()?" ⚠":""),140);
        AcademicControls.column(table,"Questions",e -> Integer.toString(e.questions().size()),80);
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->actions());
        getChildren().addAll(new HBox(10,DashboardView.label("Exams","page-title"),space,create),context,
                new FlowPane(8,8,edit,details,delete,finalizeExam,archive,refresh),table,message);
        noOffering();
    }
    public Exam selected() { return table.getSelectionModel().getSelectedItem(); }
    public void noOffering() {
        hasOffering=false; busy=false; table.getItems().clear(); context.setText("Select a course offering in the top bar.");
        message.setText(""); table.setPlaceholder(new Label("Exams belong to a teaching offering.")); actions();
    }
    public void begin(String label) { hasOffering=true; context.setText(label); table.getItems().clear(); setBusy(true); }
    public void setBusy(boolean value) { busy=value; actions(); }
    private void actions() {
        boolean unavailable=busy || !hasOffering; Exam selected=selected();
        create.setDisable(unavailable); refresh.setDisable(unavailable); table.setDisable(unavailable);
        details.setDisable(unavailable || selected==null);
        boolean draft=selected!=null && selected.status()==ExamStatus.DRAFT;
        edit.setDisable(unavailable || !draft); delete.setDisable(unavailable || !draft); finalizeExam.setDisable(unavailable || !draft);
        archive.setDisable(unavailable || selected==null || selected.status()!=ExamStatus.FINALIZED);
    }
    public void showExams(List<Exam> exams,Long selectedId) {
        table.getItems().setAll(exams); setBusy(false);
        Button add=AcademicControls.button("Create Exam","empty-create-exam","primary-button"); add.setOnAction(e -> create.fire());
        table.setPlaceholder(AcademicControls.empty("No exams yet.","Create a draft and select questions from this course's bank.",add));
        if(selectedId!=null) { exams.stream().filter(e -> e.id()==selectedId).findFirst().ifPresent(e -> table.getSelectionModel().select(e)); }
        AcademicControls.status(message,exams.size()+" saved exams · Marks shown as calculated / intended. Finalized exams are read-only.",false);
    }
    public void error(String text) { setBusy(false); AcademicControls.status(message,text,true); }
    public void loadError(String text) { table.getItems().clear(); table.setPlaceholder(new Label("Exams could not be loaded. Use Refresh.")); error(text); }
    public void details(Exam exam) {
        VBox body=new VBox(12,DashboardView.label(exam.title()+" · "+exam.status(),"section-title"),
                DashboardView.label(exam.date()+" · "+exam.durationMinutes()+" minutes · "+exam.calculatedMarks()+" / "+exam.intendedMarks()+" marks"+(exam.marksDiffer()?" · Warning: totals differ.":""),"muted"),
                DashboardView.label("Instructions: "+(exam.instructions().isBlank()?"None":exam.instructions()),"muted"));
        for(ExamQuestion q:exam.questions()) {
            body.getChildren().add(DashboardView.label(q.position()+". "+q.text()+" ("+q.marks()+" marks)","form-label"));
            for(AnswerChoice choice:q.choices()) { body.getChildren().add(DashboardView.label("• "+choice.text()+(choice.correct()?" ✓":""),"muted")); }
            body.getChildren().add(DashboardView.label("Answer: "+q.answer()+"\nExplanation: "+q.explanation()+(q.imagePath().isBlank()?"":"\nImage: "+q.imagePath()),"muted"));
        }
        body.getStyleClass().add("question-details-body");
        ScrollPane scroll=new ScrollPane(body); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(450);
        AcademicControls.details(getScene().getWindow(),"Exam Details · Saved snapshot",new VBox(scroll));
    }
}
