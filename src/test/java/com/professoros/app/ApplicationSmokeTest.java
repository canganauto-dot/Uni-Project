package com.professoros.app;

import com.professoros.view.MainView;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, headful smoke check. Run with -Pui-smoke on a graphical desktop. */
@Tag("ui")
class ApplicationSmokeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void startsApplicationNavigatesEverySectionAndRendersDashboard() throws Exception {
        assertEquals(Path.of("target", "ui-smoke-data", "professor-os.db").toAbsolutePath().normalize(),
                AppPaths.databaseFile(), "UI smoke test must use its isolated data directory");
        ProfessorOsApplication application = new ProfessorOsApplication();
        application.init();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Stage stage = new Stage();
            try {
                application.start(stage);
                assertTrue(stage.isShowing());
                var root = stage.getScene().getRoot();
                root.applyCss();
                root.layout();
                assertNotNull(root.lookup("#dashboard"));
                for (String section : MainView.SECTIONS) {
                    String id = "#nav-" + section.toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
                    ToggleButton button = (ToggleButton) root.lookup(id);
                    assertNotNull(button, section);
                    button.fire();
                    root.applyCss();
                    root.layout();
                    assertTrue(button.isSelected());
                    if ("Courses".equals(section)) {
                        assertNotNull(root.lookup("#courses-page"));
                    } else if ("Students".equals(section)) {
                        assertNotNull(root.lookup("#students-page"));
                    } else if ("Settings".equals(section)) {
                        assertNotNull(root.lookup("#terms-page"));
                    } else if ("Lectures".equals(section)) {
                        assertNotNull(root.lookup("#lectures-page"));
                    } else if ("Timeline".equals(section)) {
                        assertNotNull(root.lookup("#timeline-page"));
                    } else if ("Exam Bank".equals(section)) {
                        assertNotNull(root.lookup("#questions-page"));
                    } else if ("Exams".equals(section)) {
                        assertNotNull(root.lookup("#exams-page"));
                    } else if ("Grades".equals(section)) {
                        assertNotNull(root.lookup("#grades-page"));
                    } else if (!"Dashboard".equals(section)) {
                        assertEquals(section, ((Label) root.lookup("#placeholder-title")).getText());
                    }
                }
                ((ToggleButton) root.lookup("#nav-dashboard")).fire();
                root.applyCss();
                root.layout();
                assertNotNull(root.lookup("#dashboard"));
                saveSnapshot(root.snapshot(null, null), "dashboard.png");
                stage.setWidth(1100);
                stage.setHeight(740);
                // Native resize events need a UI pulse before the smaller scene can be inspected.
                PauseTransition resized = new PauseTransition(Duration.millis(250));
                resized.setOnFinished(event -> {
                    try {
                        root.applyCss();
                        root.layout();
                        assertTrue(stage.getScene().getWidth() > 1000);
                        assertTrue(stage.getScene().getWidth() < 1101);
                        saveSnapshot(root.snapshot(null, null), "dashboard-minimum.png");
                        completed.complete(null);
                    } catch (Throwable failure) {
                        completed.completeExceptionally(failure);
                    } finally {
                        stage.close();
                    }
                });
                resized.play();
            } catch (Throwable failure) {
                // Forward failures, including assertion errors, to JUnit's test thread.
                completed.completeExceptionally(failure);
                stage.close();
            }
        });
        try {
            completed.get(30, TimeUnit.SECONDS);
            new CourseWorkflowCheck(temporaryDirectory.resolve("courses.db")).verify();
            new AcademicWorkflowCheck(temporaryDirectory.resolve("academic.db")).verify();
            new LectureWorkflowCheck(temporaryDirectory.resolve("lectures.db")).verify();
            new QuestionWorkflowCheck(temporaryDirectory.resolve("questions.db")).verify();
            new ExamWorkflowCheck(temporaryDirectory.resolve("exams.db")).verify();
            new GradeWorkflowCheck(temporaryDirectory.resolve("grades.db")).verify();
            new ExcelWorkflowCheck(temporaryDirectory.resolve("excel")).verify();
        } finally {
            application.stop();
            Platform.exit();
        }
    }

    static void saveSnapshot(WritableImage image, String name) throws Exception {
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) {
                output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        Path directory = Path.of("target", "ui-smoke");
        Files.createDirectories(directory);
        assertTrue(ImageIO.write(output, "png", directory.resolve(name).toFile()));
    }
}
