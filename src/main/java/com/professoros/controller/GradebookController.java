package com.professoros.controller;
import com.professoros.model.*;
import com.professoros.service.*;
import com.professoros.view.*;
import java.util.*;
import java.util.function.Supplier;

/** Coordinates gradebook operations; services own validation, drafts and arithmetic. */
public final class GradebookController implements AutoCloseable {
    private final GradebookService service;
    private final GradebookView view=new GradebookView();
    private final UiTaskRunner tasks=new UiTaskRunner();
    private OfferingSummary offering;
    private GradebookDraft draft;
    private long revision;
    private boolean busy;
    public GradebookController(GradebookService service,ExcelExportService exporter) {
        this.service=service;
        view.exportExcel.setOnAction(e->{
            if(!configurable()) { return; } long offeringId=id();
            var dialog=new ExcelExportDialog(owner(),GradeCalculator.configurationMessage(draft.book()));
            dialog.onSave(()->{
                try {
                    var destination=dialog.path(); dialog.setBusy(true); busy=true;
                    tasks.run(()->{ var bytes=exporter.render(offeringId); exporter.save(bytes,destination); return destination; },path->{
                        busy=false; dialog.setBusy(false); dialog.close(); view.status("Excel report saved to "+path,false);
                    },message->{ busy=false; dialog.setBusy(false); dialog.showError(message); });
                } catch(DomainValidationException failure) { dialog.showError(failure.getMessage()); }
            }); dialog.show();
        });
        view.onChange((key,value)->{ if(draft!=null) { draft.set(key,value); summarize(false); } });
        view.save.setOnAction(e->save());
        view.discard.setOnAction(e->{ if(canLeave()) { load(); } });
        view.newCategory.setOnAction(e->category(null));
        view.editCategory.setOnAction(e->{ var selected=view.categories.getSelectionModel().getSelectedItem(); if(selected!=null) { category(selected); } });
        view.deleteCategory.setOnAction(e->{
            var selected=view.categories.getSelectionModel().getSelectedItem();
            if(selected!=null && configurable() && confirm("Delete category?",selected.name())) { long offeringId=id(); mutate(()->service.deleteCategory(offeringId,selected.id())); }
        });
        view.newAssessment.setOnAction(e->assessment(null));
        view.editAssessment.setOnAction(e->{ var selected=view.assessments.getSelectionModel().getSelectedItem(); if(selected!=null) { assessment(selected); } });
        view.deleteAssessment.setOnAction(e->{
            var selected=view.assessments.getSelectionModel().getSelectedItem();
            if(selected!=null && configurable() && confirm("Delete assessment and its grades?",selected.title()+"\nAll saved grades for this assessment will be permanently removed. This cannot be undone.")) {
                long offeringId=id(); mutate(()->service.deleteAssessment(offeringId,selected.id()));
            }
        });
        view.scale.setOnAction(e->{
            if(!configurable()) { return; } long offeringId=id();
            var dialog=new GradeForms.Scale(owner(),draft.book().scale());
            bind(dialog,()->{ var scale=dialog.input(); return ()->service.saveScale(offeringId,scale); }); dialog.show();
        });
        view.bulk.setOnAction(e->{
            if(draft==null) { return; }
            var dialog=new GradeForms.Bulk(owner(),draft.book().assessments());
            dialog.onSave(()->{
                try { var changes=service.parseBulk(draft.book(),dialog.assessmentId(),dialog.values()); changes.forEach(draft::set); summarize(true); dialog.close(); }
                catch(DomainValidationException failure) { dialog.showError(failure.getMessage()); }
            }); dialog.show();
        });
    }
    private javafx.stage.Window owner() { return view.getScene().getWindow(); }
    private long id() { return offering.offering().id(); }
    public GradebookView show(OfferingSummary selected) {
        offering=selected; draft=null; revision++;
        view.begin(selected==null?"Select a course offering to manage grades.":selected.label());
        if(selected!=null) { load(); } else { busy=false; }
        return view;
    }
    private void load() {
        if(offering==null) { return; }
        long offeringId=id(),request=++revision; busy=true; view.setBusy(true);
        tasks.run(()->service.load(offeringId),book->{
            if(request!=revision) { return; } busy=false; draft=new GradebookDraft(book); view.load(draft); summarize(true);
        },message->{ if(request==revision) { busy=false; view.setBusy(false); view.status(message,true); } });
    }
    private void summarize(boolean refresh) {
        view.dirty(draft.dirty());
        String weight=GradeCalculator.configurationMessage(draft.book());
        Map<Long,String> results=new HashMap<>();
        try {
            var scores=draft.scores();
            for(var student:draft.book().students()) {
                var result=GradeCalculator.calculate(draft.book(),student.enrollment().id(),scores);
                results.put(student.enrollment().id(),result.weightedPoints()+" /100 · "+result.letter()+" · "+result.outcome()+(result.complete()?"":" ("+result.graded()+"/"+result.required()+" graded; provisional)"));
            }
            view.summaries(results,weight,GradeCalculator.statistics(draft.book(),scores),refresh);
            view.status(draft.dirty()?"Unsaved changes. Save Grades to persist; blank clears a score.":"Saved grades. Blank = ungraded; 0 = actual zero. Zero-weight categories do not affect totals.",false);
        } catch(DomainValidationException invalid) {
            view.summaries(results,weight,"Fix invalid scores before calculating or saving.",refresh); view.status(invalid.getMessage(),true);
        }
    }
    private void save() {
        if(draft==null || !draft.dirty()) { return; }
        long offeringId=id(); var changes=draft.changes(); mutate(()->service.saveGrades(offeringId,changes));
    }
    private void mutate(Runnable action) {
        busy=true; view.setBusy(true);
        tasks.run(()->{ action.run(); return true; },ignored->load(),message->{ busy=false; view.setBusy(false); view.status(message,true); if(draft!=null) { view.dirty(draft.dirty()); } });
    }
    private boolean configurable() {
        if(draft==null || busy) { return false; }
        if(draft.dirty()) { view.status("Save or discard grade edits before exporting or changing assessment settings.",true); return false; }
        return true;
    }
    private void category(AssessmentCategory existing) {
        if(!configurable()) { return; } long offeringId=id();
        var dialog=new GradeForms.Category(owner(),existing);
        bind(dialog,()->{ String name=dialog.name(); var weight=dialog.weight(); return ()->service.saveCategory(offeringId,existing==null?null:existing.id(),name,weight); }); dialog.show();
    }
    private void assessment(Assessment existing) {
        if(!configurable()) { return; } long offeringId=id();
        var dialog=new GradeForms.AssessmentForm(owner(),draft.book().categories(),existing);
        bind(dialog,()->{ var title=dialog.title(); var maximum=dialog.maximum(); var date=dialog.date(); long category=dialog.categoryId();
            return ()->service.saveAssessment(offeringId,existing==null?null:existing.id(),category,title,maximum,date); }); dialog.show();
    }
    private void bind(AcademicFormDialog dialog,Supplier<Runnable> operation) {
        dialog.onSave(()->{
            try {
                Runnable action=operation.get(); dialog.setBusy(true); busy=true;
                tasks.run(()->{ action.run(); return true; },ignored->{ busy=false; dialog.setBusy(false); dialog.close(); load(); },
                        message->{ busy=false; dialog.setBusy(false); dialog.showError(message); });
            } catch(DomainValidationException failure) { dialog.showError(failure.getMessage()); }
        });
    }
    private boolean confirm(String title,String message) { return AcademicControls.confirm(owner(),title,message); }
    public boolean canLeave() {
        if(busy) { view.status("Wait for the current gradebook operation to finish.",false); return false; }
        if(draft==null || !draft.dirty()) { return true; }
        if(!confirm("Discard unsaved grades?","Choose Cancel to keep editing and save your grades, or Confirm to discard these changes.")) { return false; }
        draft=new GradebookDraft(draft.book()); view.load(draft); summarize(true); return true;
    }
    @Override public void close() { revision++; tasks.close(); }
}
