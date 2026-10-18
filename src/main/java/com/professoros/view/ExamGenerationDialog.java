package com.professoros.view;

import com.professoros.model.*;
import com.professoros.service.ExamGenerator;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;

public final class ExamGenerationDialog extends AcademicFormDialog {
    private final TextField count=text("generation-count","10");
    private final Map<Difficulty,TextField> difficulties=new EnumMap<>(Difficulty.class);
    private final Map<QuestionType,TextField> types=new EnumMap<>(QuestionType.class);
    private final Map<String,TextField> topics=new LinkedHashMap<>();
    public ExamGenerationDialog(Window owner,List<Question> bank) {
        super(owner,"Generate Question Selection");
        getDialogPane().setPrefWidth(650);
        field("Question count *",count);
        TabPane tabs=new TabPane(new Tab("Difficulty",quotaFields(Arrays.asList(Difficulty.values()),difficulties)),
                new Tab("Type",quotaFields(Arrays.asList(QuestionType.values()),types)),
                new Tab("Topic",quotaFields(bank.stream().map(q -> ExamGenerator.topicKey(q.topic())).distinct().sorted().toList(),topics)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.setId("generation-tabs"); tabs.setPrefHeight(260);
        field("Exact counts",tabs);
        Label rules=DashboardView.label("Blank distribution = unrestricted.\nEntered counts must sum to the question count.\nOmitted categories then mean zero. Marks are not constrained.", "muted");
        rules.setMinHeight(Region.USE_PREF_SIZE);
        field("Rules",rules);
        setSaveLabel("Generate");
    }
    private <T> ScrollPane quotaFields(List<T> keys,Map<T,TextField> fields) {
        GridPane grid=new GridPane(); grid.setHgap(16); grid.setVgap(10);
        int row=0;
        for(T key:keys) {
            TextField value=text("quota-"+key.toString().replace(' ','-'),""); value.setPromptText("Unspecified"); value.setPrefWidth(140);
            fields.put(key,value);
            grid.addRow(row++,DashboardView.label(key.toString().isBlank()?"(Untagged)":key.toString(),"form-label"),value);
        }
        ScrollPane scroll=new ScrollPane(grid); scroll.setFitToWidth(true); return scroll;
    }
    public GenerationRequest input() { return new GenerationRequest(number(count.getText()),values(difficulties),values(types),values(topics)); }
    private static <T> Map<T,Integer> values(Map<T,TextField> fields) {
        Map<T,Integer> result=new LinkedHashMap<>();
        fields.forEach((key,field) -> { if(!field.getText().isBlank()) { result.put(key,number(field.getText())); } });
        return result;
    }
    private static Integer number(String text) { try { return Integer.valueOf(text.strip()); } catch(NumberFormatException invalid) { return null; } }
}
