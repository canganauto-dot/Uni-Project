package com.professoros.controller;

import com.professoros.demo.DemoDashboard;
import com.professoros.view.DashboardView;
import com.professoros.view.MainView;
import com.professoros.view.CoursesView;
import com.professoros.service.CourseService;

/** Navigation only; no SQL, persistence or domain calculations. */
public final class MainController implements AutoCloseable {
    private final MainView view;
    private final DashboardView dashboard;
    private final CoursesView courses = new CoursesView();
    private final CourseController courseController;

    public MainController(MainView view, DemoDashboard data, CourseService service) {
        this.view = view;
        dashboard = new DashboardView(data);
        courseController = new CourseController(service, courses, dashboard::setCourseCount);
        view.onNavigate(this::navigate);
        navigate("Dashboard");
    }

    private void navigate(String section) {
        if ("Dashboard".equals(section)) {
            view.showPage(section, dashboard);
            courseController.refresh();
        } else if ("Courses".equals(section)) {
            view.showPage(section, courses);
            courseController.refresh();
        } else {
            view.showPlaceholder(section);
        }
    }

    @Override
    public void close() {
        courseController.close();
    }
}
