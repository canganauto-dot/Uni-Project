package com.professoros.controller;

import com.professoros.pdf.*;
import com.professoros.service.ExamPdfService;
import com.professoros.view.*;
import javafx.stage.Window;

/** PDF interactions only; renderer/export service own content and filesystem behavior. */
public final class ExamPdfController implements AutoCloseable {
    private final ExamPdfService service;
    private final UiTaskRunner tasks=new UiTaskRunner();
    public ExamPdfController(ExamPdfService service) { this.service=service; }
    public void open(Window owner,long offeringId,long examId,java.util.function.Consumer<String> error) {
        tasks.run(() -> service.load(offeringId,examId),source -> show(owner,source),error);
    }
    private void show(Window owner,ExamPrintSource source) {
        ExamPdfDialog dialog=new ExamPdfDialog(owner,source);
        final RenderedExamPdf[] rendered={null};
        final int[] page={0};
        dialog.onChanged(() -> { rendered[0]=null; dialog.invalidate(); });
        dialog.preview.setOnAction(e -> {
            ExamPrintProfile profile=dialog.profile(); PdfKind kind=dialog.kind.getValue();
            dialog.setBusy(true); dialog.status("Rendering "+kind+"…",false);
            tasks.run(() -> {
                var pdf=service.render(source,profile,kind);
                return new Preview(pdf,service.preview(pdf,0));
            },result -> {
                rendered[0]=result.pdf(); page[0]=0; dialog.setBusy(false); dialog.showPage(result.png(),0,result.pdf().pageCount());
                dialog.status(result.pdf().warnings().isEmpty()?"Preview ready. Save this PDF, or select the other document type.":String.join(" ",result.pdf().warnings()),false);
            },message -> { rendered[0]=null; dialog.setBusy(false); dialog.invalidate(); dialog.status(message,true); });
        });
        dialog.previous.setOnAction(e -> turn(dialog,rendered[0],page,-1));
        dialog.next.setOnAction(e -> turn(dialog,rendered[0],page,1));
        dialog.save.setOnAction(e -> {
            var pdf=rendered[0]; if(pdf==null) { return; }
            var destination=dialog.chooseDestination(pdf.kind()); if(destination==null) { return; }
            boolean acknowledged=dialog.acknowledge.isSelected(); dialog.setBusy(true);
            tasks.run(() -> { service.saveAs(pdf,destination,acknowledged); return destination; },path -> {
                dialog.setBusy(false); dialog.status(pdf.kind()+" saved to "+path,false);
            },message -> { dialog.setBusy(false); dialog.status(message,true); });
        });
        dialog.show();
    }
    private record Preview(RenderedExamPdf pdf,byte[] png) { }
    private void turn(ExamPdfDialog dialog,RenderedExamPdf pdf,int[] page,int delta) {
        if(pdf==null) { return; }
        int selected=page[0]+delta; dialog.setBusy(true);
        tasks.run(() -> service.preview(pdf,selected),png -> {
            page[0]=selected; dialog.setBusy(false); dialog.showPage(png,selected,pdf.pageCount());
        },message -> { dialog.setBusy(false); dialog.status(message,true); });
    }
    @Override public void close() { tasks.close(); }
}
