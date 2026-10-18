package com.professoros.controller;

import com.professoros.model.*;
import com.professoros.service.ExamService;
import com.professoros.view.*;
import java.util.List;
import java.util.concurrent.Callable;

public final class ExamController implements AutoCloseable {
    private final ExamService service;
    private final ExamsView view=new ExamsView();
    private final UiTaskRunner tasks=new UiTaskRunner();
    private OfferingSummary offering;
    private long revision;
    private record EditorData(Exam exam,List<Question> bank) { }
    public ExamController(ExamService service) {
        this.service=service;
        view.create.setOnAction(e -> edit(null)); view.edit.setOnAction(e -> { if(view.selected()!=null) { edit(view.selected().id()); } });
        view.refresh.setOnAction(e -> load(null)); view.details.setOnAction(e -> details());
        view.delete.setOnAction(e -> change("Delete draft exam?","This permanently deletes the draft and its saved question selection.",0));
        view.finalizeExam.setOnAction(e -> change("Finalize exam?","This locks its content and records question usage once. Review the calculated / intended marks before confirming.",1));
        view.archive.setOnAction(e -> change("Archive exam?","The finalized exam and its question history will be preserved.",2));
    }
    public ExamsView show(OfferingSummary selected) {
        offering=selected; revision++;
        if(selected==null) { view.noOffering(); }
        else { view.begin(selected.label()); load(null); }
        return view;
    }
    private void load(Long selectedId) {
        if(offering==null) { return; }
        long scope=offering.offering().id(),request=++revision; view.setBusy(true);
        tasks.run(() -> service.findByOffering(scope), rows -> { if(request==revision) { view.showExams(rows,selectedId); } },
                message -> { if(request==revision) { view.loadError(message); } });
    }
    private void edit(Long examId) {
        if(offering==null) { return; }
        long scope=offering.offering().id(),request=++revision; String label=offering.label(); view.setBusy(true);
        tasks.run(() -> new EditorData(examId==null?null:service.findById(scope,examId),service.bankForExam(scope,examId)), data -> {
            if(request!=revision) { return; }
            view.setBusy(false);
            if(data.exam()!=null && data.exam().status()!=ExamStatus.DRAFT) { view.error("This exam is no longer a draft. Refresh to see its status."); return; }
            ExamBuilderDialog dialog=new ExamBuilderDialog(view.getScene().getWindow(),label,data.bank(),data.exam());
            dialog.onSave(() -> {
                ExamInput input=dialog.input(); List<Long> ids=dialog.questionIds(); dialog.setBusy(true);
                tasks.run(() -> service.save(scope,examId,input,ids), saved -> {
                    dialog.setBusy(false); dialog.close();
                    if(offering!=null && offering.offering().id()==scope) { load(saved.id()); }
                }, message -> { dialog.setBusy(false); dialog.showError(message); });
            });
            dialog.generate.setOnAction(e -> {
                if(!dialog.questionIds().isEmpty() && !AcademicControls.confirm(dialog.getDialogPane().getScene().getWindow(),
                        "Replace selected questions?","Generation replaces this form's current selection only after it succeeds. Nothing is persisted until Save.")) { return; }
                ExamGenerationDialog generator=new ExamGenerationDialog(dialog.getDialogPane().getScene().getWindow(),data.bank());
                generator.onSave(() -> {
                    GenerationRequest constraints=generator.input(); generator.setBusy(true);
                    tasks.run(() -> service.generate(scope,examId,constraints), questions -> {
                        generator.setBusy(false); generator.close(); dialog.replaceSelection(questions);
                    }, message -> { generator.setBusy(false); generator.showError(message); });
                });
                generator.show();
            });
            dialog.show();
        },message -> { if(request==revision) { view.error(message); } });
    }
    private void details() {
        Exam selected=view.selected(); if(selected==null || offering==null) { return; }
        long scope=offering.offering().id(),request=++revision; view.setBusy(true);
        tasks.run(() -> service.findById(scope,selected.id()),exam -> {
            if(request==revision) { view.setBusy(false); view.details(exam); }
        },message -> { if(request==revision) { view.error(message); } });
    }
    private void change(String title,String message,int action) {
        Exam selected=view.selected(); if(selected==null || offering==null) { return; }
        long scope=offering.offering().id();
        if(!AcademicControls.confirm(view.getScene().getWindow(),title,selected.title()+"\n"+message+
                "\nCalculated "+selected.calculatedMarks()+" / intended "+selected.intendedMarks()+" marks.")) { return; }
        long request=++revision; view.setBusy(true);
        Callable<Void> operation=() -> {
            if(action==0) { service.delete(scope,selected.id()); }
            else if(action==1) { service.finalizeExam(scope,selected.id()); }
            else { service.archive(scope,selected.id()); }
            return null;
        };
        tasks.run(operation,ignored -> { if(request==revision) { load(action==0?null:selected.id()); } },
                error -> { if(request==revision) { view.error(error); } });
    }
    @Override public void close() { revision++; tasks.close(); }
}
