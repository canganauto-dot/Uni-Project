package com.professoros.controller;
import com.professoros.excel.StudentWorkbookReader;
import com.professoros.model.*;
import com.professoros.service.StudentImportService;
import com.professoros.view.StudentImportDialog;
import javafx.stage.Window;
public final class StudentImportController implements AutoCloseable {
    private final StudentImportService service;
    private final UiTaskRunner tasks=new UiTaskRunner();
    public StudentImportController(StudentImportService service) { this.service=service; }
    public void open(Window owner,Long offeringId,String destination,Runnable refresh) {
        var dialog=new StudentImportDialog(owner,destination); final StudentImportPreview[] reviewed={null};
        dialog.onInvalidated(()->reviewed[0]=null);
        dialog.load.setOnAction(e->{
            try {
                var file=dialog.path(); dialog.beginLoad(); dialog.setBusy(true);
                tasks.run(()->new StudentWorkbookReader().read(file),workbook->{ dialog.setBusy(false); dialog.loaded(workbook); },
                        message->{ dialog.setBusy(false); dialog.status(message,true); });
            } catch(DomainValidationException error) { dialog.status(error.getMessage(),true); }
        });
        dialog.preview.setOnAction(e->{
            var sheet=dialog.sheet.getValue(); int header=dialog.header.getValue(); var mapping=dialog.mapping(); dialog.clearReview(); dialog.setBusy(true);
            tasks.run(()->service.preview(sheet,header,mapping,offeringId),result->{ reviewed[0]=result; dialog.setBusy(false); dialog.showPreview(result); },
                    message->{ reviewed[0]=null; dialog.setBusy(false); dialog.status(message,true); });
        });
        dialog.confirm.setOnAction(e->{
            var snapshot=reviewed[0]; if(snapshot==null) { return; } dialog.clearReview(); dialog.setBusy(true);
            tasks.run(()->service.confirm(snapshot),result->{ reviewed[0]=null; dialog.setBusy(false); dialog.finished(result); refresh.run(); },
                    message->{ reviewed[0]=null; dialog.setBusy(false); dialog.status(message,true); });
        });
        dialog.show();
    }
    @Override public void close() { tasks.close(); }
}
