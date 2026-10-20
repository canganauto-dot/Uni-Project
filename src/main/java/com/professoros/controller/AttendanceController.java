package com.professoros.controller;
import com.professoros.model.*;
import com.professoros.service.*;
import com.professoros.view.*;
import javafx.scene.control.*;
public final class AttendanceController implements AutoCloseable {
    private final AttendanceService service;
    private final AttendanceView view=new AttendanceView();
    private final UiTaskRunner tasks=new UiTaskRunner();
    private AttendanceBook book;
    private AttendanceDraft draft;
    private OfferingSummary offering;
    private Lecture selected;
    private boolean busy,selecting;
    private long revision;
    public AttendanceController(AttendanceService service) {
        this.service=service;
        view.lecture.valueProperty().addListener((o,old,value)->{
            if(selecting)return;
            if(!canLeave()) {selecting=true;view.lecture.setValue(old);selecting=false;return;}
            selected=value;draft=value==null?null:new AttendanceDraft(book,value.id());view.draft(draft);view.setBusy(false);
        });
        view.onChange((id,value)->{if(draft!=null){draft.set(id,value);view.dirty();}});
        view.apply.setOnAction(e->{
            if(draft==null || selected.status()==LectureStatus.CANCELLED)return;
            if(AcademicControls.confirm(owner(),"Mark entire roster?","Apply "+view.bulk.getValue()+" to all "+book.students().size()+" enrolled students, including hidden and withdrawn rows? Changes are saved only when you choose Save attendance.")) {
                book.students().forEach(s->draft.set(s.enrollment().id(),AttendanceView.parse(view.bulk.getValue())));view.draft(draft);
            }
        });
        view.reload.setOnAction(e->{if(canLeave())load();});
        view.save.setOnAction(e->{if(draft==null || !draft.dirty())return;long id=selected.id(),offeringId=book.offeringId();var changes=draft.changes();busy=true;view.setBusy(true);
            tasks.run(()->{service.save(offeringId,id,changes);return true;},done->load(),message->{busy=false;view.setBusy(false);view.status(message,true);});
        });
        view.history.setOnAction(e->view.showHistory());
    }
    private javafx.stage.Window owner(){return view.getScene().getWindow();}
    public AttendanceView show(OfferingSummary value) {offering=value;revision++;selected=null;book=null;draft=null;selecting=true;view.clear();selecting=false;view.context(value==null?"Select a course offering to manage attendance.":value.label());busy=false;if(value!=null)load();return view;}
    private void load() {
        if(offering==null)return;long request=++revision,id=offering.offering().id();Long previous=selected==null?null:selected.id();busy=true;view.setBusy(true);
        tasks.run(()->service.load(id),value->{if(request!=revision)return;book=value;busy=false;selecting=true;
            view.load(book);selected=book.lectures().stream().filter(l->previous!=null && l.id()==previous).findFirst().orElse(book.lectures().isEmpty()?null:book.lectures().getFirst());view.lecture.setValue(selected);selecting=false;
            draft=selected==null?null:new AttendanceDraft(book,selected.id());view.draft(draft);view.setBusy(false);
            if(selected==null)view.status("No lectures yet. Create a lecture in Lectures, then reload attendance.",false);
        },message->{if(request==revision){busy=false;view.setBusy(false);view.status(message,true);}});
    }
    public boolean canLeave() {
        if(busy){view.status("Wait for the attendance operation to finish.",false);return false;}
        if(draft==null || !draft.dirty())return true;
        if(!AcademicControls.confirm(owner(),"Discard unsaved attendance?","Cancel to keep editing, or Confirm to discard unsaved attendance marks."))return false;
        draft=new AttendanceDraft(book,selected.id());view.draft(draft);return true;
    }
    @Override public void close(){revision++;tasks.close();}
}
