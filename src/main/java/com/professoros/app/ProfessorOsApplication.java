package com.professoros.app;

import com.professoros.controller.MainController;
import com.professoros.database.Database;
import com.professoros.demo.DemoDashboard;
import com.professoros.view.MainView;
import com.professoros.repository.CourseRepository;
import com.professoros.service.CourseService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfessorOsApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(ProfessorOsApplication.class.getName());
    private Exception startupFailure;
    private CourseService courseService;
    private com.professoros.service.AcademicServices academicServices;
    private MainController controller;

    @Override
    public void init() {
        // JavaFX calls init off the UI thread. No long-lived connection is kept.
        try {
            Database database = new Database(AppPaths.databaseFile());
            database.initialize();
            courseService = new CourseService(new CourseRepository(database));
            academicServices = com.professoros.service.AcademicServices.create(database);
        } catch (SQLException | IllegalArgumentException | SecurityException exception) {
            startupFailure = exception;
            LOGGER.log(Level.SEVERE, "Database startup failed", exception);
        }
    }

    @Override
    public void start(Stage stage) {
        if (startupFailure != null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Professor OS — startup problem");
            alert.setHeaderText("The local database could not be opened or upgraded.");
            alert.setContentText("Check that the data folder is writable and has free space. "
                    + "You can choose another folder with PROFESSOR_OS_DATA_DIR. "
                    + "If a migration failed, keep the existing database and review the launch log. Do not delete your data.");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        MainView view = new MainView();
        controller = new MainController(view, new DemoDashboard(), courseService, academicServices);
        Scene scene = new Scene(view, 1360, 900);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/theme.css"), "Missing application stylesheet").toExternalForm());
        stage.setTitle("Professor OS | Dashboard");
        stage.setMinWidth(1100);
        stage.setMinHeight(740);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> { if (!controller.canClose()) { event.consume(); } });
        stage.show();
        LOGGER.info("Professor OS window shown — academic terms, offerings, students and enrollment ready");
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
