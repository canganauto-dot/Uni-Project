package com.professoros.view;

import com.professoros.model.*;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.util.*;

/** One form for creation and editing; validation remains in QuestionService. */
public final class QuestionFormDialog extends AcademicFormDialog {
    private final ComboBox<Course> course;
    private final ComboBox<QuestionType> type = new ComboBox<>(FXCollections.observableArrayList(QuestionType.values()));
    private final ComboBox<Difficulty> difficulty = new ComboBox<>(FXCollections.observableArrayList(Difficulty.values()));
    private final TextField marks, topic, imagePath;
    private final TextArea questionText, answer, explanation;
    private final ComboBox<String> trueFalse = new ComboBox<>(FXCollections.observableArrayList("TRUE", "FALSE"));
    private final VBox choiceRows = new VBox(10);
    private final ToggleGroup correctChoice = new ToggleGroup();
    private final List<ChoiceRow> choices = new ArrayList<>();
    private final Button addChoice = new Button("Add choice");

    public QuestionFormDialog(Window owner, List<Course> courses, Course selected, Question existing) {
        super(owner, existing == null ? "New Question" : "Edit Question");
        getDialogPane().setPrefWidth(720);
        course = courseSelector(courses, "Select course"); course.setId("question-course");
        course.setValue(existing == null ? selected : courses.stream().filter(c -> c.id() == existing.courseId()).findFirst().orElse(null));
        type.setId("question-type"); type.setValue(existing == null ? QuestionType.MCQ : existing.type());
        difficulty.setId("question-difficulty"); difficulty.setValue(existing == null ? Difficulty.MEDIUM : existing.difficulty());
        marks = text("question-marks", existing == null ? "1" : existing.marks().toPlainString());
        topic = text("question-topic", existing == null ? "" : existing.topic());
        questionText = notes("question-text", existing == null ? "" : existing.text()); questionText.setPrefRowCount(8);
        answer = notes("question-answer", existing == null ? "" : existing.answer());
        explanation = notes("question-explanation", existing == null ? "" : existing.explanation());
        imagePath = text("question-image", existing == null ? "" : existing.imagePath());
        trueFalse.setId("question-true-false"); trueFalse.setPromptText("Choose the correct answer");
        if (existing != null && existing.type() == QuestionType.TRUE_FALSE) { trueFalse.setValue(existing.answer()); }
        field("Course *", course); field("Type *", type); field("Difficulty *", difficulty);
        field("Marks *", marks); field("Topic", topic);
        Tab prompt = new Tab("Question *", questionText);
        VBox answerBody = new VBox(8, new Label("Answer / model answer (optional except True/False)"), trueFalse, answer,
                new Label("Explanation (optional)"), explanation);
        Tab answerTab = new Tab("Answer", answerBody);
        addChoice.setId("add-question-choice"); addChoice.getStyleClass().add("secondary-button");
        addChoice.setOnAction(event -> addChoice("", false));
        ScrollPane choiceScroll = new ScrollPane(choiceRows); choiceScroll.setFitToWidth(true); choiceScroll.setPrefHeight(145);
        choiceScroll.setMinHeight(100);
        Tab choicesTab = new Tab("MCQ Choices", new VBox(8, new Label("2–10 distinct choices. Select one correct answer."), choiceScroll, addChoice));
        if (existing != null && !existing.choices().isEmpty()) {
            existing.choices().forEach(c -> addChoice(c.text(), c.correct()));
        } else { addChoice("", false); addChoice("", false); }
        Button browse = AcademicControls.button("Choose image…", "browse-question-image", "secondary-button");
        browse.setOnAction(event -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("Select a local question image");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            var file = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
            if (file != null) { imagePath.setText(file.getAbsolutePath()); }
        });
        Tab imageTab = new Tab("Image", new VBox(12, new Label("Optional local image path"), imagePath, browse,
                DashboardView.label("The image is referenced, not copied. Keep it at this path. Preview and export are not implemented.", "muted")));
        TabPane tabs = new TabPane(prompt, choicesTab, answerTab, imageTab);
        tabs.setId("question-tabs"); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefHeight(240); field("Content", tabs);
        Runnable configure = () -> {
            boolean mcq = type.getValue() == QuestionType.MCQ, tf = type.getValue() == QuestionType.TRUE_FALSE;
            choicesTab.setDisable(!mcq);
            answer.setDisable(mcq || tf);
            trueFalse.setVisible(tf); trueFalse.setManaged(tf);
        };
        type.valueProperty().addListener((obs, old, value) -> configure.run()); configure.run();
    }
    public QuestionInput input() {
        BigDecimal value;
        try { value = new BigDecimal(marks.getText().strip()); } catch (NumberFormatException failure) { value = null; }
        return new QuestionInput(course.getValue() == null ? null : course.getValue().id(), type.getValue(),
                questionText.getText(), topic.getText(), difficulty.getValue(), value,
                type.getValue() == QuestionType.TRUE_FALSE ? trueFalse.getValue() : answer.getText(),
                explanation.getText(), imagePath.getText(),
                type.getValue() == QuestionType.MCQ ? choices.stream().map(row -> new AnswerChoice(row.text.getText(), row.correct.isSelected())).toList() : List.of());
    }
    private void addChoice(String text, boolean correct) {
        ChoiceRow row = new ChoiceRow(text, correct); choices.add(row); choiceRows.getChildren().add(row);
        addChoice.setDisable(choices.size() >= 10);
    }
    private final class ChoiceRow extends HBox {
        private final TextArea text;
        private final RadioButton correct = new RadioButton("Correct");
        ChoiceRow(String value, boolean selected) {
            super(8); text = notes("choice-text", value); text.setPrefRowCount(2); text.setMinWidth(0);
            text.setPrefColumnCount(18); HBox.setHgrow(text, Priority.ALWAYS);
            correct.setToggleGroup(correctChoice); correct.setSelected(selected); correct.setId("choice-correct");
            correct.setMinWidth(Region.USE_PREF_SIZE);
            Button remove = new Button("Remove");
            remove.setMinWidth(Region.USE_PREF_SIZE); remove.getStyleClass().add("secondary-button");
            remove.setOnAction(event -> { correct.setToggleGroup(null); choices.remove(this); choiceRows.getChildren().remove(this); addChoice.setDisable(false); });
            getChildren().addAll(correct, text, remove);
        }
    }
    public static ComboBox<Course> courseSelector(List<Course> courses, String emptyLabel) {
        ComboBox<Course> combo = new ComboBox<>(FXCollections.observableArrayList(courses));
        combo.setPromptText(emptyLabel);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(Course course) { return course == null ? emptyLabel : course.courseCode() + " — " + course.courseName(); }
            @Override public Course fromString(String value) { throw new UnsupportedOperationException("Course selection is not editable"); }
        });
        return combo;
    }
}
