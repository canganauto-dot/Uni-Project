package com.professoros.view;

import com.professoros.model.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Local editing buffer. Nothing is persisted until Save succeeds. */
public final class ExamBuilderDialog extends AcademicFormDialog {
    private final TextField title,date,duration,intended;
    private final TextArea instructions;
    private final ObservableList<ExamQuestion> selected=FXCollections.observableArrayList();
    private final TableView<ExamQuestion> selection=AcademicControls.table("exam-selected-questions");
    private final Label totals=DashboardView.label("","muted");
    private final Map<Long,ExamQuestion> savedSnapshots=new HashMap<>();
    public final Button generate=AcademicControls.button("Generate selection…","generate-exam","secondary-button");
    private final TabPane tabs;

    public ExamBuilderDialog(Window owner,String offering,List<Question> bank,Exam existing) {
        super(owner,existing==null?"New Exam":"Edit Draft Exam");
        getDialogPane().setPrefWidth(1000); getDialogPane().getStyleClass().add("exam-builder"); setHeaderText((existing==null?"New Exam":"Edit Draft")+" · "+offering);
        title=text("exam-title",existing==null?"":existing.title());
        date=text("exam-date",existing==null?"":existing.date().toString()); date.setPromptText("yyyy-MM-dd");
        duration=text("exam-duration",existing==null?"60":Integer.toString(existing.durationMinutes()));
        intended=text("exam-intended",existing==null?"100":existing.intendedMarks().toPlainString());
        instructions=notes("exam-instructions",existing==null?"":existing.instructions()); instructions.setPrefRowCount(8);
        var metadata=new GridPane(); metadata.setHgap(12);
        for(int width:new int[]{40,24,18,18}) { var column=new ColumnConstraints();column.setPercentWidth(width);column.setMinWidth(0);metadata.getColumnConstraints().add(column); }
        metadata.addRow(0,AcademicControls.filter("Exam title *",title),AcademicControls.filter("Date *",date),
                AcademicControls.filter("Minutes *",duration),AcademicControls.filter("Intended marks *",intended));
        section(metadata);
        TableView<Question> available=AcademicControls.table("exam-bank-questions"); available.setPrefHeight(190); available.setMinHeight(100);
        available.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        AcademicControls.column(available,"Question",Question::text,400);
        AcademicControls.column(available,"Type",q -> q.type().toString(),120);
        AcademicControls.column(available,"Difficulty",q -> q.difficulty().toString(),90);
        AcademicControls.column(available,"Marks",q -> q.marks().toPlainString(),65);
        ComboBox<QuestionType> type=new ComboBox<>(FXCollections.observableArrayList(QuestionType.values())); type.setPromptText("All types"); type.setId("exam-type-filter");
        ComboBox<Difficulty> difficulty=new ComboBox<>(FXCollections.observableArrayList(Difficulty.values())); difficulty.setPromptText("All difficulties"); difficulty.setId("exam-difficulty-filter");
        filterLabel(type,"All types"); filterLabel(difficulty,"All difficulties");
        TextField topic=text("exam-topic-filter",""); topic.setPromptText("Topic contains…"); topic.setPrefWidth(160);
        TextField search=text("exam-question-search",""); search.setPromptText("Search question…");
        FilteredList<Question> filtered=new FilteredList<>(FXCollections.observableArrayList(bank));
        available.setItems(filtered);
        Runnable filter=() -> filtered.setPredicate(q -> q.matches(search.getText()) && (type.getValue()==null || q.type()==type.getValue())
                && (difficulty.getValue()==null || q.difficulty()==difficulty.getValue())
                && q.topic().toLowerCase(Locale.ROOT).contains(topic.getText().strip().toLowerCase(Locale.ROOT)));
        type.valueProperty().addListener((o,a,b)->filter.run()); difficulty.valueProperty().addListener((o,a,b)->filter.run());
        topic.textProperty().addListener((o,a,b)->filter.run()); search.textProperty().addListener((o,a,b)->filter.run());
        Button clear=AcademicControls.button("Clear","clear-exam-filters","secondary-button");
        clear.setOnAction(e -> { type.setValue(null); difficulty.setValue(null); topic.clear(); search.clear(); });
        Button add=AcademicControls.button("Add selected","add-exam-question","primary-button");
        add.disableProperty().bind(available.getSelectionModel().selectedItemProperty().isNull());
        available.setPlaceholder(DashboardView.label(bank.isEmpty()?"No bank questions for this course. Add them in Exam Bank first.":"No matching questions. Clear filters to try again.","muted"));
        add.setOnAction(e -> {
            for(Question q:List.copyOf(available.getSelectionModel().getSelectedItems())) {
                if(selected.stream().noneMatch(s -> s.questionId()==q.id())) { selected.add(snapshot(q,selected.size()+1)); }
            }
            updateTotals();
        });
        AcademicControls.activateOnEnter(available,add);
        search.setPrefWidth(210);
        available.setTooltip(new Tooltip("Select rows with Ctrl/Shift, then Add selected. Enter adds the selected rows."));
        var bankPane=new VBox(10,
                AcademicControls.toolbar(AcademicControls.filter("Search questions",search),AcademicControls.filter("Type",type),
                        AcademicControls.filter("Difficulty",difficulty),AcademicControls.filter("Topic",topic),clear),
                available,AcademicControls.toolbar(add,generate));
        bankPane.getStyleClass().add("builder-panel");
        AcademicControls.searchShortcut(bankPane,search);
        selection.setItems(selected); selection.setPrefHeight(200); selection.setMinHeight(100);
        AcademicControls.column(selection,"No.",q->Integer.toString(selected.indexOf(q)+1),50);
        selection.getColumns().getFirst().setMinWidth(55);
        selection.getColumns().getFirst().setMaxWidth(55);
        AcademicControls.column(selection,"Question",ExamQuestion::text,450);
        AcademicControls.column(selection,"Type",q -> q.type().toString(),120);
        AcademicControls.column(selection,"Marks",q -> q.marks().toPlainString(),70);
        selection.getColumns().forEach(c -> c.setSortable(false));
        selection.setPlaceholder(AcademicControls.empty("Your exam starts here","Add questions from the bank, or generate a selection with constraints.",null));
        Button remove=AcademicControls.button("Remove","remove-exam-question","danger-button");
        Button up=AcademicControls.button("Move up","exam-question-up","secondary-button");
        Button down=AcademicControls.button("Move down","exam-question-down","secondary-button");
        for(Button button:List.of(remove,up,down)) { button.disableProperty().bind(selection.getSelectionModel().selectedItemProperty().isNull()); }
        remove.setOnAction(e -> { selected.remove(selection.getSelectionModel().getSelectedItem()); updateTotals(); });
        up.setOnAction(e -> move(-1)); down.setOnAction(e -> move(1));
        var composition=new VBox(10,DashboardView.label("Compose your exam","section-title"),
                DashboardView.label("The order below is the order on the exam. Saved questions retain their exam snapshots.","small-muted"),
                selection,AcademicControls.toolbar(up,down,remove)); composition.getStyleClass().add("builder-panel");
        var selectedTab=new Tab("Composition",composition);
        selectedTab.textProperty().bind(javafx.beans.binding.Bindings.size(selected).asString("Composition · %d questions"));
        tabs=new TabPane(new Tab("Question bank",bankPane),selectedTab,new Tab("Instructions",instructions));
        tabs.setId("exam-builder-tabs"); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.setPrefHeight(365);
        totals.setId("exam-mark-totals");
        section(tabs); footer(totals);
        intended.textProperty().addListener((o,a,b)->updateTotals());
        if(existing!=null) {
            existing.questions().forEach(q -> savedSnapshots.put(q.questionId(),q));
            selected.setAll(existing.questions());
        }
        updateTotals();
    }
    private ExamQuestion snapshot(Question q,int position) {
        return savedSnapshots.containsKey(q.id())?savedSnapshots.get(q.id()).atPosition(position):ExamQuestion.snapshot(q,position);
    }
    public void replaceSelection(List<Question> questions) {
        selected.clear();
        for(Question q:questions) { selected.add(snapshot(q,selected.size()+1)); }
        tabs.getSelectionModel().select(1); updateTotals();
    }
    private void move(int delta) {
        int from=selection.getSelectionModel().getSelectedIndex(),to=from+delta;
        if(from>=0 && to>=0 && to<selected.size()) {
            Collections.swap(selected,from,to); selection.getSelectionModel().select(to); updateTotals();
        }
    }
    private void updateTotals() {
        BigDecimal actual=selected.stream().map(ExamQuestion::marks).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal target=decimal(intended.getText());
        boolean mismatch=target==null || actual.compareTo(target)!=0;
        AcademicControls.status(totals,selected.size()+" questions · Calculated "+actual.toPlainString()+" marks · Intended "
                +(target==null?"invalid":target.toPlainString())+(mismatch?" · Warning: totals differ.":""),mismatch);
    }
    public List<Long> questionIds() { return selected.stream().map(ExamQuestion::questionId).toList(); }
    public ExamInput input() {
        LocalDate parsed; Integer minutes;
        try { parsed=LocalDate.parse(date.getText().strip()); } catch(DateTimeParseException invalid) { parsed=null; }
        try { minutes=Integer.valueOf(duration.getText().strip()); } catch(NumberFormatException invalid) { minutes=null; }
        return new ExamInput(title.getText(),parsed,minutes,decimal(intended.getText()),instructions.getText());
    }
    private static BigDecimal decimal(String text) { try { return new BigDecimal(text.strip()); } catch(NumberFormatException invalid) { return null; } }
    private static <T> void filterLabel(ComboBox<T> combo,String placeholder) {
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(T item,boolean empty) {
                super.updateItem(item,empty); setText(item==null?placeholder:item.toString());
            }
        });
    }
}
