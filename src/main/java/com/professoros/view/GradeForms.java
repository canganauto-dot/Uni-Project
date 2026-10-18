package com.professoros.view;
import com.professoros.model.*;
import com.professoros.service.GradebookService;
import javafx.scene.control.*;
import javafx.stage.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GradeForms {
    private GradeForms() { }
    public static final class Category extends AcademicFormDialog {
        private final TextField name,weight;
        public Category(Window owner,AssessmentCategory existing) {
            super(owner,existing==null?"New assessment category":"Edit category");
            name=text("category-name",existing==null?"":existing.name()); weight=text("category-weight",existing==null?"":existing.weight().toPlainString());
            field("Name *",name); field("Weight % *",weight);
        }
        public String name() { return name.getText(); }
        public BigDecimal weight() { return GradebookService.number(weight.getText(),"Weight"); }
    }
    public static final class AssessmentForm extends AcademicFormDialog {
        private final TextField title,maximum,date;
        private final ComboBox<AssessmentCategory> category=new ComboBox<>();
        public AssessmentForm(Window owner,List<AssessmentCategory> categories,Assessment existing) {
            super(owner,existing==null?"New assessment":"Edit assessment");
            title=text("assessment-title",existing==null?"":existing.title());
            maximum=text("assessment-maximum",existing==null?"100":existing.maximumMarks().toPlainString());
            date=text("assessment-date",existing==null?LocalDate.now().toString():existing.date().toString());
            category.setId("assessment-category"); category.getItems().setAll(categories);
            if(existing!=null) { categories.stream().filter(c->c.id()==existing.categoryId()).findFirst().ifPresent(category::setValue); }
            else { category.getSelectionModel().selectFirst(); }
            field("Title *",title); field("Category *",category); field("Maximum marks *",maximum); field("Date YYYY-MM-DD *",date);
        }
        public String title() { return title.getText(); }
        public long categoryId() { if(category.getValue()==null) { throw new DomainValidationException("Create and select a category first."); } return category.getValue().id(); }
        public BigDecimal maximum() { return GradebookService.number(maximum.getText(),"Maximum marks"); }
        public LocalDate date() { try { return LocalDate.parse(date.getText().strip()); } catch(java.time.format.DateTimeParseException e) { throw new DomainValidationException("Use a valid date in YYYY-MM-DD format."); } }
    }
    public static final class Scale extends AcademicFormDialog {
        private final TextField a,b,c,d,pass;
        public Scale(Window owner,GradingScale scale) {
            super(owner,"Grading scale · inclusive minimum percentages");
            a=text("scale-a",scale.a().toPlainString()); b=text("scale-b",scale.b().toPlainString());
            c=text("scale-c",scale.c().toPlainString()); d=text("scale-d",scale.d().toPlainString()); pass=text("scale-pass",scale.pass().toPlainString());
            field("A minimum % *",a); field("B minimum % *",b); field("C minimum % *",c); field("D minimum % *",d); field("Pass minimum % *",pass);
            field("F",new Label("Below D. Incomplete results receive no letter or pass/fail."));
        }
        public GradingScale input() { return new GradingScale(n(a),n(b),n(c),n(d),n(pass)); }
        private BigDecimal n(TextField field) { return GradebookService.number(field.getText(),"Threshold"); }
    }
    public static final class Bulk extends AcademicFormDialog {
        private final ComboBox<Assessment> assessment=new ComboBox<>();
        private final TextArea values=notes("grade-bulk-values","");
        public Bulk(Window owner,List<Assessment> assessments) {
            super(owner,"Bulk grade entry");
            assessment.setId("grade-bulk-assessment"); assessment.getItems().setAll(assessments); assessment.getSelectionModel().selectFirst();
            values.setPromptText("2026001\t18.5\n2026002\t0"); values.setPrefRowCount(8);
            field("Assessment *",assessment); field("Student number TAB score",values);
            field("Blank score",new Label("Clears a grade. Apply stages changes; Save Grades persists them."));
            setSaveLabel("Apply to gradebook");
        }
        public long assessmentId() { if(assessment.getValue()==null) { throw new DomainValidationException("Create an assessment first."); } return assessment.getValue().id(); }
        public String values() { return values.getText(); }
    }
}
