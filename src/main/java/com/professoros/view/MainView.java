package com.professoros.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
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
    private final com.professoros.theme.ThemeManager themes;
    private final javafx.scene.control.Button themeToggle=AcademicControls.button("Theme", "theme-toggle", "secondary-button");
    public com.professoros.theme.ThemeManager themes() { return themes; }
    public static final List<String> SECTIONS = List.of("Dashboard", "Courses", "Students", "Lectures",
            "Exam Bank", "Exams", "Grades", "Attendance", "Analytics", "Timeline", "Settings");

    private final Map<String, ToggleButton> navigation = new LinkedHashMap<>();
    private final ScrollPane content = new ScrollPane();
    private final Label sectionLabel = DashboardView.label("Dashboard", "breadcrumb");
    private final javafx.scene.control.ComboBox<com.professoros.model.OfferingSummary> offering = new javafx.scene.control.ComboBox<>();
    private boolean updatingOffering;
    private boolean collapsed;
    private VBox sidebarNode;
    private final java.util.List<Node> sidebarLabels=new java.util.ArrayList<>();
    private final javafx.scene.control.Button collapseToggle=AcademicControls.button("", "sidebar-toggle", "secondary-button");
    private final javafx.scene.control.Button openOffering = AcademicControls.button("Open", "open-active-offering", "secondary-button");

    public MainView() {
        this(new com.professoros.service.ThemePreferences(com.professoros.app.AppPaths.databaseFile().resolveSibling("appearance.properties")));
    }
    public MainView(com.professoros.service.ThemePreferences preferences) {
        themes=new com.professoros.theme.ThemeManager(this,preferences);
        themeToggle.textProperty().bind(themes.modeProperty().map(mode->"◐ " + mode));
        themeToggle.setAccessibleText("Cycle appearance: Light, Dark, System");
        themeToggle.setOnAction(e->themes.select(themes.mode().next()));
        var themeTip=new javafx.scene.control.Tooltip();
        themeTip.textProperty().bind(themes.messageProperty().map(message->message.isEmpty()?"Cycle Light, Dark and System appearance. Also available in Settings.":message));
        themeToggle.setTooltip(themeTip);
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
        sidebarLabels.add(brandText);
        HBox brand = new HBox(10, logo, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");
        VBox links = new VBox(5);
        ToggleGroup group = new ToggleGroup();
        String lastGroup="";
        for (String section : List.of("Dashboard","Courses","Students","Lectures","Timeline","Exam Bank","Exams","Grades","Attendance","Analytics","Settings")) {
            String category=switch(section) {
                case "Dashboard","Courses","Students" -> "WORKSPACE";
                case "Lectures","Timeline" -> "TEACHING";
                case "Exam Bank","Exams","Grades" -> "ASSESSMENT";
                case "Settings" -> "PREFERENCES";
                default -> "INSIGHTS";
            };
            if(!category.equals(lastGroup)) {
                Label heading=DashboardView.label(category,"navigation-group");
                sidebarLabels.add(heading); links.getChildren().add(heading); lastGroup=category;
            }
            ToggleButton button = new ToggleButton(section, NavigationIcon.create(section));
            button.setId("nav-" + section.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
            button.setAccessibleText(section); button.setTooltip(new Tooltip(section));
            button.setToggleGroup(group);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("nav-button");
            navigation.put(section, button);
            links.getChildren().add(button);
        }
        VBox footer = new VBox(7, DashboardView.label("OFFLINE WORKSPACE", "sidebar-caption"),
                DashboardView.label("Made for your semester", "sidebar-detail"));
        sidebarLabels.add(footer);
        ScrollPane scroll=new ScrollPane(links); scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("navigation-scroll");
        VBox.setVgrow(scroll,Priority.ALWAYS);
        VBox sidebar = new VBox(12, brand, scroll, footer);
        sidebarNode=sidebar;
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(236);
        sidebar.setMinWidth(236);
        return sidebar;
    }

    private HBox topBar() {
        collapseToggle.setGraphic(NavigationIcon.create("Collapse"));
        collapseToggle.setOnAction(e->setSidebarCollapsed(!collapsed));
        collapseToggle.setAccessibleText("Collapse sidebar"); collapseToggle.setTooltip(new Tooltip("Collapse sidebar"));
        VBox location = new VBox(4, DashboardView.label("WORKSPACE", "eyebrow"), sectionLabel);
        location.setMinWidth(95);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        offering.setId("active-offering");
        offering.setPromptText("Select a course offering");
        offering.setPrefWidth(330);
        offering.setAccessibleText("Active teaching offering");
        offering.setTooltip(new javafx.scene.control.Tooltip("Select an offering to open its overview and students."));
        openOffering.disableProperty().bind(offering.valueProperty().isNull());
        HBox context = new HBox(8, offering, openOffering);
        context.setAlignment(Pos.CENTER_LEFT);
        offering.setMinWidth(150);
        VBox teaching=new VBox(4,DashboardView.label("ACTIVE COURSE OFFERING","context-caption"),context);
        HBox bar = new HBox(16, collapseToggle, location, spacer, teaching, themeToggle);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        return bar;
    }

    public boolean isSidebarCollapsed() { return collapsed; }
    public void setSidebarCollapsed(boolean value) {
        collapsed=value;
        sidebarNode.setPrefWidth(value?76:236); sidebarNode.setMinWidth(value?76:236);
        sidebarLabels.forEach(node->{node.setVisible(!value);node.setManaged(!value);});
        navigation.values().forEach(button->{
            button.setContentDisplay(value?ContentDisplay.GRAPHIC_ONLY:ContentDisplay.LEFT);
            button.setAlignment(value?Pos.CENTER:Pos.CENTER_LEFT);
        });
        collapseToggle.setAccessibleText(value?"Expand sidebar":"Collapse sidebar");
        collapseToggle.setTooltip(new Tooltip(collapseToggle.getAccessibleText()));
    }

    public void onNavigate(Consumer<String> handler) {
        navigation.forEach((section, button) -> button.setOnAction(event -> handler.accept(section)));
    }

    public void showPage(String section, Node page) {
        sectionLabel.setText(section);
        navigation.get(section).setSelected(true);
        content.setContent(page);
        content.setFitToHeight(java.util.Set.of("Courses", "Students", "Settings", "Lectures", "Timeline", "Exam Bank", "Exams", "Grades", "Attendance").contains(section));
        content.setVvalue(0);
    }

    public void showPlaceholder(String section) {
        Label title = DashboardView.label(section, "page-title");
        title.setId("placeholder-title");
        VBox page = new VBox(16, DashboardView.label("PLANNED WORKSPACE", "eyebrow"), title,
                DashboardView.label("This workspace is planned. Its features are not implemented yet.", "muted"),
                DashboardView.label("Use Dashboard to return to your saved teaching overview.", "small-muted"));
        page.getStyleClass().add("page");
        showPage(section, page);
    }

    public void onOffering(java.util.function.Consumer<com.professoros.model.OfferingSummary> action) {
        openOffering.setOnAction(event -> { if (offering.getValue() != null) { action.accept(offering.getValue()); } });
        offering.setOnAction(event -> {
            if (!updatingOffering && offering.getValue() != null) { action.accept(offering.getValue()); }
        });
    }

    public void offeringChoices(java.util.List<com.professoros.model.OfferingSummary> choices, com.professoros.model.OfferingSummary active) {
        updatingOffering = true;
        try { offering.getItems().setAll(choices); offering.setValue(active); }
        finally { updatingOffering = false; }
        offering.setTooltip(new Tooltip(active == null ? "Select an offering to open its overview and students." : active.label()));
    }

    public void offeringError(String message) { offering.setTooltip(new javafx.scene.control.Tooltip(message)); }
    public String currentSection() { return sectionLabel.getText(); }
    public void selectSection(String section) { navigation.get(section).setSelected(true); }
}
