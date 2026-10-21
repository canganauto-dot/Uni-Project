package com.professoros.view;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** Original 20px line icons; no font, image or external icon dependency. */
final class NavigationIcon {
    private NavigationIcon() { }
    static StackPane create(String name) {
        String path=switch(name) {
            case "Dashboard" -> "M2 2 H8 V8 H2 Z M12 2 H18 V8 H12 Z M2 12 H8 V18 H2 Z M12 12 H18 V18 H12 Z";
            case "Courses" -> "M10 5 Q5 1 2 3 V16 Q6 14 10 18 Q14 14 18 16 V3 Q15 1 10 5 V18";
            case "Students" -> "M7 9 A3 3 0 1 0 7 3 A3 3 0 1 0 7 9 M1 18 V16 Q1 11 7 11 Q13 11 13 16 V18 M14 3 Q20 4 16 9 M15 12 Q19 13 19 18";
            case "Lectures" -> "M2 3 H18 V14 H2 Z M10 14 V18 M6 18 H14 M5 7 H15 M5 10 H11";
            case "Exam Bank" -> "M4 2 H15 V16 H4 Z M7 5 H12 M7 8 H12 M7 11 H10 M1 5 V19 H12";
            case "Exams" -> "M5 3 H16 V18 H3 V3 H5 M7 2 H12 V5 H7 Z M6 9 H13 M6 12 H13 M6 15 H10";
            case "Grades" -> "M3 2 H17 V18 H3 Z M6 6 H14 M6 10 H8 M12 10 H14 M6 14 H8 M12 14 H14";
            case "Attendance" -> "M3 4 H17 V18 H3 Z M6 2 V6 M14 2 V6 M3 8 H17 M6 12 L9 15 L14 10";
            case "Analytics" -> "M2 2 V18 H18 M6 14 V10 M10 14 V6 M14 14 V3";
            case "Settings" -> "M2 5 H18 M2 15 H18 M6 2 V8 M14 12 V18";
            case "Collapse" -> "M3 3 H17 V17 H3 Z M8 3 V17 M13 7 L10 10 L13 13";
            default -> throw new IllegalArgumentException("Unknown navigation icon: "+name);
        };
        var icon=new SVGPath(); icon.setContent(path); icon.getStyleClass().add("navigation-icon");
        var box=new StackPane(icon); box.setMinSize(20,20); box.setPrefSize(20,20); box.setMaxSize(20,20); box.setMouseTransparent(true);
        return box;
    }
}
