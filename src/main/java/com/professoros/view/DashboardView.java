package com.professoros.view;

import com.professoros.model.DashboardOverview;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Saved academic overview. No sample data or implied audit history. */
public final class DashboardView extends VBox {
    private final Label courseCount=label("…","metric-value");
    private final Label courseCountDetail=label("Loading saved courses…","small-muted");
    private final Label students=label("…","metric-value"),offerings=label("…","metric-value"),enrolled=label("—","metric-value");
    private final Label activeOffering=label("Active offering: none selected. Choose one in the top bar.","muted");
    private final Label state=label("Loading your workspace…","small-muted");
    private final VBox progress=panel("Teaching progress","Lecture completion · active offering");
    private final VBox upcoming=panel("Coming up","Planned lectures and exams · active offering");
    private final VBox recent=panel("Recently updated","Saved lectures and exams · not an activity log");
    public final Button refresh=AcademicControls.button("Refresh overview","refresh-dashboard","secondary-button");
    public DashboardView() {
        super(22); setId("dashboard"); getStyleClass().addAll("page","dashboard-page");
        var title=new VBox(6,label("YOUR WORKSPACE, AT A GLANCE","eyebrow"),label("Ready for your next class.","dashboard-title"),label("A focused view of your teaching, built from your saved records.","muted"));
        var spacer=new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
        var heading=new HBox(16,title,spacer,refresh); heading.setAlignment(Pos.CENTER_LEFT); title.setMinWidth(0);
        activeOffering.setId("dashboard-active-offering");
        var context=new HBox(10,NavigationIcon.create("Courses"),activeOffering); context.setAlignment(Pos.CENTER_LEFT); context.getStyleClass().add("dashboard-context");
        var metrics=grid(4); courseCount.setId("dashboard-course-count"); students.setId("dashboard-student-count"); offerings.setId("dashboard-offering-count"); enrolled.setId("dashboard-enrollment-count");
        metrics.add(card("Catalog courses",courseCount,courseCountDetail,"Courses"),0,0);
        metrics.add(card("Students",students,label("Saved student directory","small-muted"),"Students"),1,0);
        metrics.add(card("Course offerings",offerings,label("All terms and sections","small-muted"),"Timeline"),2,0);
        metrics.add(card("Active enrollments",enrolled,label("Selected offering only","small-muted"),"Attendance"),3,0);
        var panels=grid(2); panels.add(progress,0,0); panels.add(upcoming,1,0);
        state.setId("dashboard-load-status"); getChildren().addAll(heading,context,metrics,panels,recent,state);
        clearDetails("Select a course offering in the top bar to see its teaching overview.");
    }
    private static VBox card(String title,Label value,Label detail,String icon) {
        var space=new Region(); HBox.setHgrow(space,Priority.ALWAYS);
        var header=new HBox(8,label(title,"metric-label"),space,NavigationIcon.create(icon)); header.setAlignment(Pos.CENTER_LEFT);
        var card=new VBox(12,header,value,detail); card.setMinWidth(0); card.getStyleClass().addAll("metric-card","dashboard-metric"); return card;
    }
    public void setCourseCount(Integer value) { courseCount.setText(value==null?"—":value.toString()); courseCountDetail.setText(value==null?"Saved courses unavailable":"Reusable course definitions"); }
    public void setActiveOffering(String value) { activeOffering.setText(value==null?"Active offering: none selected. Choose one in the top bar.":"Active offering: "+value); }
    public void loading() { state.setText("Refreshing saved records…"); refresh.setDisable(true); }
    public void failed(String message) { students.setText("—"); offerings.setText("—"); enrolled.setText("—"); clearDetails("Saved overview unavailable. Refresh to try again."); AcademicControls.status(state,message,true); refresh.setDisable(false); }
    public void show(DashboardOverview value) {
        students.setText(Integer.toString(value.students())); offerings.setText(Integer.toString(value.offerings())); enrolled.setText(value.activeEnrollments()==null?"—":value.activeEnrollments().toString());
        clearDetails("Select a course offering in the top bar to see its teaching overview.");
        if(value.progress()!=null) {
            reset(progress); var p=value.progress();
            var percent=label(String.format(java.util.Locale.ROOT,"%.0f%%",p.completionPercent()),"progress-headline");
            var bar=new ProgressBar(p.completionPercent()/100); bar.setMaxWidth(Double.MAX_VALUE);
            progress.getChildren().addAll(percent,bar,label(p.completed()+" completed · "+p.partial()+" partial · "+p.scheduledTotal()+" scheduled","entry-title"),label(p.scheduleStatus(),"muted"),label("Cancelled lectures excluded. Partial lectures are not counted as completed.","small-muted"));
            entries(upcoming,value.upcoming(),"No upcoming lectures or exams.",false);
            entries(recent,value.recentlyUpdated(),"No saved lecture or exam updates yet.",true);
        }
        AcademicControls.status(state,"Saved records · as of "+value.asOf()+" · Refresh to pick up changes. No demo data.",false); refresh.setDisable(false);
    }
    private void clearDetails(String message) { for(var panel:List.of(progress,upcoming,recent)) { reset(panel); var empty=label(message,"muted"); empty.getStyleClass().add("dashboard-empty"); panel.getChildren().add(empty); } }
    private static void reset(VBox panel) { panel.getChildren().remove(1,panel.getChildren().size()); }
    private static void entries(VBox panel,List<DashboardOverview.Activity> activities,String empty,boolean updated) {
        reset(panel); if(activities.isEmpty()) { panel.getChildren().add(label(empty,"muted")); return; }
        for(var activity:activities) {
            var date=label(updated?activity.kind():activity.date().format(DateTimeFormatter.ofPattern("dd MMM",java.util.Locale.ENGLISH)),"activity-marker"); date.setMinWidth(64);
            var text=new VBox(5,label(activity.title(),"entry-title"),label(activity.detail()+(updated?" · Updated "+activity.updatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate():""),"small-muted")); text.setMinWidth(0); HBox.setHgrow(text,Priority.ALWAYS);
            var row=new HBox(14,date,text); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("dashboard-activity"); panel.getChildren().add(row);
        }
    }
    private static GridPane grid(int count) { var grid=new GridPane(); grid.setHgap(16); grid.setVgap(16); for(int i=0;i<count;i++) { var col=new ColumnConstraints(); col.setPercentWidth(100.0/count); col.setMinWidth(0); col.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().add(col); } return grid; }
    private static VBox panel(String title,String description) { var panel=new VBox(16,new VBox(5,label(title,"section-title"),label(description,"small-muted"))); panel.setMinWidth(0); panel.getStyleClass().add("panel"); return panel; }
    static Label label(String text,String styleClass) { var label=new Label(text); label.getStyleClass().add(styleClass); label.setWrapText(true); return label; }
}
