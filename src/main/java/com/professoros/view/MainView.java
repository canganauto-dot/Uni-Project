package com.professoros.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MainView extends BorderPane {
    public static final List<String> SECTIONS = List.of("Dashboard", "Courses", "Students", "Lectures",
            "Exam Bank", "Exams", "Grades", "Attendance", "Analytics", "Timeline", "Settings");

    private final Map<String, ToggleButton> navigation = new LinkedHashMap<>();
    private final ScrollPane content = new ScrollPane();
    private final Label sectionLabel = DashboardView.label("Dashboard", "breadcrumb");

    public MainView() {
        getStyleClass().add("workspace");
        setLeft(sidebar());
        content.setFitToWidth(true);
        content.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        content.getStyleClass().add("content-scroll");
        BorderPane main = new BorderPane(content);
        main.setTop(topBar());
        setCenter(main);
    }

    private VBox sidebar() {
        Label logo = DashboardView.label("P", "brand-mark");
        VBox brandText = new VBox(3, DashboardView.label("Professor OS", "brand-title"),
                DashboardView.label("TEACHING WORKSPACE", "brand-caption"));
        HBox brand = new HBox(10, logo, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");
        VBox links = new VBox(5);
        ToggleGroup group = new ToggleGroup();
        for (String section : SECTIONS) {
            ToggleButton button = new ToggleButton(section);
            button.setId("nav-" + section.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
            button.setToggleGroup(group);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("nav-button");
            navigation.put(section, button);
            links.getChildren().add(button);
        }
        Region space = new Region();
        VBox.setVgrow(space, Priority.ALWAYS);
        VBox footer = new VBox(7, DashboardView.label("OFFLINE WORKSPACE", "sidebar-caption"),
                DashboardView.label("Course management · Phase 2", "sidebar-detail"));
        VBox sidebar = new VBox(22, brand, DashboardView.label("WORKSPACE", "sidebar-caption"), links, space, footer);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(228);
        sidebar.setMinWidth(228);
        return sidebar;
    }

    private HBox topBar() {
        VBox location = new VBox(4, DashboardView.label("Professor OS", "topbar-title"), sectionLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label course = DashboardView.label("Current course: not selected", "course-placeholder");
        TextField search = new TextField();
        search.setPromptText("Search · planned");
        search.setAccessibleText("Search is planned and unavailable in this foundation");
        search.setDisable(true);
        search.setPrefWidth(195);
        search.getStyleClass().add("search-field");
        HBox bar = new HBox(22, location, spacer, course, search);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        return bar;
    }

    public void onNavigate(Consumer<String> handler) {
        navigation.forEach((section, button) -> button.setOnAction(event -> handler.accept(section)));
    }

    public void showPage(String section, Node page) {
        sectionLabel.setText(section);
        navigation.get(section).setSelected(true);
        content.setContent(page);
        content.setFitToHeight("Courses".equals(section));
        content.setVvalue(0);
    }

    public void showPlaceholder(String section) {
        Label title = DashboardView.label(section, "page-title");
        title.setId("placeholder-title");
        VBox page = new VBox(16, DashboardView.label("PLANNED WORKSPACE", "eyebrow"), title,
                DashboardView.label("This workspace is planned. Its features are not implemented yet.", "muted"),
                DashboardView.label("Use Dashboard to return to the sample overview.", "small-muted"));
        page.getStyleClass().add("page");
        showPage(section, page);
    }
}
