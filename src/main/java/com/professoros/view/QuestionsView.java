package com.professoros.view;

import com.professoros.model.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;

public final class QuestionsView extends VBox {
    public final TableView<Question> table = AcademicControls.table("questions-table");
    public final TextField search = new TextField();
    public final ComboBox<Course> course = QuestionFormDialog.courseSelector(List.of(), "All courses");
    public final ComboBox<QuestionType> type = new ComboBox<>();
    public final ComboBox<Difficulty> difficulty = new ComboBox<>();
    public final Button create = AcademicControls.button("+ New Question", "new-question", "primary-button");
    public final Button edit = AcademicControls.button("Edit", "edit-question", "secondary-button");
    public final Button details = AcademicControls.button("View Details", "question-details", "secondary-button");
    public final Button delete = AcademicControls.button("Delete", "delete-question", "danger-button");
    public final Button refresh = AcademicControls.button("Refresh", "refresh-questions", "secondary-button");
    public final Label status = DashboardView.label("", "muted");
    private final ObservableList<Question> rows = FXCollections.observableArrayList();
    private final FilteredList<Question> filtered = new FilteredList<>(rows);
    private List<Course> courses = List.of();
    private boolean failed;

    public QuestionsView() {
        super(16); setId("questions-page"); getStyleClass().add("page");
        search.setId("question-search"); search.setPromptText("Search question, topic or answer"); search.setPrefWidth(300);
        course.setId("filter-question-course"); course.setPrefWidth(290);
        type.setId("filter-question-type"); type.setPromptText("All types"); type.getItems().addAll(QuestionType.values());
        difficulty.setId("filter-question-difficulty"); difficulty.setPromptText("All difficulties"); difficulty.getItems().addAll(Difficulty.values());
        filterLabel(course, "All courses", c -> c.courseCode() + " — " + c.courseName());
        filterLabel(type, "All types", QuestionType::toString);
        filterLabel(difficulty, "All difficulties", Difficulty::toString);
        Button reset = AcademicControls.button("Clear filters", "clear-question-filters", "secondary-button");
        reset.setOnAction(event -> clearFilters());
        search.textProperty().addListener((obs, old, value) -> filter());
        course.valueProperty().addListener((obs, old, value) -> filter());
        type.valueProperty().addListener((obs, old, value) -> filter());
        difficulty.valueProperty().addListener((obs, old, value) -> filter());
        SortedList<Question> sorted = new SortedList<>(filtered); sorted.comparatorProperty().bind(table.comparatorProperty()); table.setItems(sorted);
        AcademicControls.column(table, "Course", q -> courseCode(q.courseId()), 95);
        AcademicControls.column(table, "Question", q -> q.text().replace('\n', ' '), 350);
        AcademicControls.column(table, "Topic", Question::topic, 120);
        AcademicControls.column(table, "Type", q -> q.type().toString(), 120);
        AcademicControls.column(table, "Difficulty", q -> q.difficulty().toString(), 90);
        TableColumn<Question, java.math.BigDecimal> marks = new TableColumn<>("Marks");
        marks.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(row.getValue().marks())); marks.setPrefWidth(65);
        table.getColumns().add(marks);
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        details.disableProperty().bind(edit.disableProperty()); delete.disableProperty().bind(edit.disableProperty());
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        getChildren().addAll(new HBox(12, DashboardView.label("Exam Question Bank", "page-title"), space, create),
                DashboardView.label("Reusable catalog questions · Independent of the active teaching offering.", "muted"),
                search, new FlowPane(10, 10, course, type, difficulty, reset),
                new HBox(10, edit, details, delete, refresh), table, status);
        placeholder();
    }
    public List<Course> courses() { return courses; }
    private static <T> void filterLabel(ComboBox<T> combo, String emptyLabel, java.util.function.Function<T, String> label) {
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty); setText(item == null ? emptyLabel : label.apply(item));
            }
        });
    }
    public void showQuestions(List<Question> questions, List<Course> available, Long selectedId) {
        failed = false;
        Long previous = course.getValue() == null ? null : course.getValue().id();
        courses = List.copyOf(available); course.getItems().setAll(courses);
        course.setValue(courses.stream().filter(c -> previous != null && c.id() == previous).findFirst().orElse(null));
        rows.setAll(questions); create.setDisable(courses.isEmpty());
        if (selectedId != null) {
            clearFilters();
            rows.stream().filter(q -> q.id() == selectedId).findFirst().ifPresent(q -> { table.getSelectionModel().select(q); table.scrollTo(q); });
        }
        filter();
        AcademicControls.status(status, questions.size() + " saved questions · Usage is recorded only by future exam workflows.", false);
    }
    public void loadError(String message) {
        failed = true; rows.clear(); placeholder(); AcademicControls.status(status, message + " Use Refresh to retry.", true);
    }
    private String courseCode(long id) { return courses.stream().filter(c -> c.id() == id).map(Course::courseCode).findFirst().orElse("Unavailable course"); }
    private void clearFilters() { search.clear(); course.setValue(null); type.setValue(null); difficulty.setValue(null); }
    private void filter() {
        filtered.setPredicate(q -> q.matches(search.getText()) && (course.getValue() == null || q.courseId() == course.getValue().id())
                && (type.getValue() == null || q.type() == type.getValue()) && (difficulty.getValue() == null || q.difficulty() == difficulty.getValue()));
        placeholder();
    }
    private void placeholder() {
        Button add = AcademicControls.button("Create Question", "empty-create-question", "primary-button");
        add.setOnAction(event -> create.fire());
        table.setPlaceholder(AcademicControls.empty(failed ? "Questions could not be loaded." : rows.isEmpty() ? "No questions yet." : "No matching questions.",
                failed ? "Use Refresh to try again." : courses.isEmpty() ? "Create a catalog course in Courses first." :
                rows.isEmpty() ? "Add a reusable question for one of your courses." : "Try another search or clear the filters.",
                !failed && rows.isEmpty() && !courses.isEmpty() ? add : null));
    }
    public void showDetails(Window owner, Question question) {
        VBox body = new VBox(12);
        body.getChildren().addAll(DashboardView.label(courseCode(question.courseId()) + " · " + question.type() + " · " + question.difficulty() + " · " + question.marks() + " marks", "section-title"),
                DashboardView.label(question.text(), "form-label"),
                DashboardView.label("Topic: " + (question.topic().isBlank() ? "Not specified" : question.topic()), "muted"));
        for (int i = 0; i < question.choices().size(); i++) {
            AnswerChoice choice = question.choices().get(i);
            body.getChildren().add(DashboardView.label((i + 1) + ". " + choice.text() + (choice.correct() ? "  ✓ Correct" : ""), "form-label"));
        }
        body.getChildren().addAll(DashboardView.label("Answer: " + (question.answer().isBlank() ? question.type() == QuestionType.MCQ ? "Marked above" : "Not provided" : question.answer()), "form-label"),
                DashboardView.label("Explanation: " + (question.explanation().isBlank() ? "Not provided" : question.explanation()), "muted"),
                DashboardView.label("Image path: " + (question.imagePath().isBlank() ? "None" : question.imagePath()), "muted"),
                DashboardView.label("Usage count: " + question.usageCount() + " · Last used: " + (question.lastUsedDate() == null ? "Never" : question.lastUsedDate()), "muted"),
                DashboardView.label("Created: " + CoursesView.formatTime(question.createdAt()) + " · Updated: " + CoursesView.formatTime(question.updatedAt()), "small-muted"));
        body.getStyleClass().add("question-details-body");
        ScrollPane scroll = new ScrollPane(body); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(430);
        AcademicControls.details(owner, "Question Details", new VBox(scroll));
    }
}
