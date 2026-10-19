package com.professoros.view;
import com.professoros.model.DomainValidationException;
import javafx.scene.control.*;
import javafx.stage.*;
import java.nio.file.*;
public final class ExcelExportDialog extends AcademicFormDialog {
    private final TextField path=text("excel-export-path","");
    public ExcelExportDialog(Window owner,String warning) {
        super(owner,"Export saved Gradebook to Excel");
        field("Report",description("Grades, Assessment Breakdown and Statistics. Attendance is not available."));
        field("Calculation",description(warning));
        field("Destination *",path);
        var browse=AcademicControls.button("Choose destination…","choose-excel-export","secondary-button");
        browse.setOnAction(e->{ var chooser=new FileChooser(); chooser.setInitialFileName("gradebook.xlsx"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook","*.xlsx")); var file=chooser.showSaveDialog(owner); if(file!=null) { path.setText(file.getAbsolutePath()); } });
        field("Save as",browse);
        field("Content",description("Saved snapshot; editing Excel cells does not recalculate results. Existing files are never overwritten."));
        setSaveLabel("Export");
    }
    private static Label description(String text) {
        Label label=new Label(text); label.setWrapText(true); label.setMinWidth(0); return label;
    }
    public Path path() {
        if(path.getText().isBlank()) { throw new DomainValidationException("Choose an export destination."); }
        try { return Path.of(path.getText().strip()); } catch(InvalidPathException e) { throw new DomainValidationException("Choose a valid destination path."); }
    }
}
