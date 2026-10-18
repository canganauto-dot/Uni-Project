package com.professoros.view;

import com.professoros.pdf.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.io.ByteArrayInputStream;

public final class ExamPdfDialog extends Dialog<Void> {
    public final Button preview=AcademicControls.button("Generate Preview","preview-exam-pdf","primary-button");
    public final Button save=AcademicControls.button("Save PDF As…","save-exam-pdf","primary-button");
    public final Button previous=AcademicControls.button("Previous","pdf-previous","secondary-button");
    public final Button next=AcademicControls.button("Next","pdf-next","secondary-button");
    public final ComboBox<PdfKind> kind=new ComboBox<>();
    public final CheckBox acknowledge=new CheckBox("I acknowledge the mark-total mismatch.");
    public final Label message=DashboardView.label("Enter print details, then generate a preview.","muted");
    private final TextField university=new TextField(),department=new TextField(),professor=new TextField(),logo=new TextField();
    private final ImageView image=new ImageView();
    private final Label pageLabel=new Label("No preview");
    private final GridPane fields=new GridPane();
    private boolean busy,ready;
    private int page,pages;
    private final boolean mismatch;
    public ExamPdfDialog(Window owner,ExamPrintSource source) {
        initOwner(owner); setTitle("Exam PDF Preview and Export"); setHeaderText(source.exam().title()+" · Saved exam snapshots");
        CourseDialogs.style(getDialogPane()); getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setPrefWidth(1000);
        mismatch=source.exam().marksDiffer();
        university.setId("pdf-university"); department.setId("pdf-department"); professor.setId("pdf-professor"); logo.setId("pdf-logo");
        department.setText(source.department());
        university.setPromptText("University name"); professor.setPromptText("Professor name");
        fields.setHgap(12); fields.setVgap(8);
        fields.getColumnConstraints().addAll(new ColumnConstraints(105),new ColumnConstraints());
        fields.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);
        fields.addRow(0,new Label("University *"),university); fields.addRow(1,new Label("Department *"),department);
        fields.addRow(2,new Label("Professor *"),professor);
        Button browse=AcademicControls.button("Choose logo…","choose-pdf-logo","secondary-button");
        browse.setOnAction(e -> {
            FileChooser chooser=new FileChooser(); chooser.setTitle("Choose university logo");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG or JPEG","*.png","*.jpg","*.jpeg"));
            var file=chooser.showOpenDialog(getDialogPane().getScene().getWindow()); if(file!=null) { logo.setText(file.getAbsolutePath()); }
        });
        HBox logoField=new HBox(8,logo,browse); HBox.setHgrow(logo,Priority.ALWAYS);
        fields.addRow(3,new Label("Logo (optional)"),logoField);
        kind.setId("pdf-kind"); kind.getItems().setAll(PdfKind.values()); kind.setValue(PdfKind.STUDENT);
        acknowledge.setId("pdf-acknowledge"); acknowledge.setVisible(mismatch); acknowledge.setManaged(mismatch);
        Label warning=DashboardView.label(mismatch?"Warning: calculated "+source.exam().calculatedMarks()+" marks differs from intended "+source.exam().intendedMarks()+". Both will be printed.":"Total marks: "+source.exam().calculatedMarks(),"muted");
        image.setPreserveRatio(true); image.setFitWidth(610);
        ScrollPane scroll=new ScrollPane(image); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(320); scroll.setMinHeight(200);
        image.setId("exam-pdf-image"); scroll.setId("exam-pdf-preview");
        HBox controls=new HBox(10,kind,preview,save,previous,pageLabel,next);
        VBox body=new VBox(10,fields,warning,acknowledge,controls,scroll,message);
        getDialogPane().setContent(body);
        acknowledge.selectedProperty().addListener((o,a,b)->actions());
        setOnCloseRequest(e -> { if(busy) { e.consume(); } });
        actions();
    }
    public ExamPrintProfile profile() { return new ExamPrintProfile(university.getText(),department.getText(),professor.getText(),logo.getText()); }
    public void onChanged(Runnable action) {
        for(TextField field:java.util.List.of(university,department,professor,logo)) { field.textProperty().addListener((o,a,b)->action.run()); }
        kind.valueProperty().addListener((o,a,b)->action.run());
    }
    public void invalidate() { ready=false; image.setImage(null); pageLabel.setText("No preview"); message.setText("Generate a new preview for these settings."); actions(); }
    public void setBusy(boolean value) { busy=value; fields.setDisable(value); kind.setDisable(value); getDialogPane().lookupButton(ButtonType.CLOSE).setDisable(value); actions(); }
    public void showPage(byte[] png,int index,int count) {
        page=index; pages=count; ready=true; image.setImage(new Image(new ByteArrayInputStream(png)));
        pageLabel.setText((page+1)+" / "+pages); actions();
    }
    public void status(String text,boolean error) { AcademicControls.status(message,text,error); }
    private void actions() {
        preview.setDisable(busy); save.setDisable(busy || !ready || (mismatch && !acknowledge.isSelected()));
        previous.setDisable(busy || !ready || page==0); next.setDisable(busy || !ready || page>=pages-1);
    }
    public java.nio.file.Path chooseDestination(PdfKind selected) {
        FileChooser chooser=new FileChooser(); chooser.setTitle("Save "+selected+" PDF");
        chooser.setInitialFileName(selected==PdfKind.STUDENT?"student-exam.pdf":"answer-key.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document","*.pdf"));
        var file=chooser.showSaveDialog(getDialogPane().getScene().getWindow());
        return file==null?null:file.toPath();
    }
}
