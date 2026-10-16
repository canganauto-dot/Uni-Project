package com.professoros.controller;

import com.professoros.demo.DemoDashboard;
import com.professoros.view.DashboardView;
import com.professoros.view.MainView;

/** Navigation only; no SQL, persistence or domain calculations. */
public final class MainController {
    private final MainView view;
    private final DashboardView dashboard;

    public MainController(MainView view, DemoDashboard data) {
        this.view = view;
        dashboard = new DashboardView(data);
        view.onNavigate(this::navigate);
        navigate("Dashboard");
    }

    private void navigate(String section) {
        if ("Dashboard".equals(section)) {
            view.showPage(section, dashboard);
        } else {
            view.showPlaceholder(section);
        }
    }
}
