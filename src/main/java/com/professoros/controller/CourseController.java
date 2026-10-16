package com.professoros.controller;

import com.professoros.model.Course;
import com.professoros.model.CourseValidationException;
import com.professoros.repository.CourseStorageException;
import com.professoros.service.CourseService;
import com.professoros.view.CourseDialogs;
import com.professoros.view.CourseFormDialog;
import com.professoros.view.CoursesView;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Coordinates course UI and background service calls. No SQL or business validation. */
public final class CourseController implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(CourseController.class.getName());
    private final CourseService service;
    private final CoursesView view;
    private final Consumer<Integer> countChanged;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("course-worker").factory());
    private boolean busy;

    public CourseController(CourseService service, CoursesView view, Consumer<Integer> countChanged) {
        this.service = service;
        this.view = view;
        this.countChanged = countChanged;
        view.onCreate(() -> edit(null));
        view.onEdit(() -> edit(view.selectedCourse()));
        view.onDelete(this::deleteSelected);
        view.onDetails(() -> {
            Course selected = view.selectedCourse();
            if (selected != null) {
                CourseDialogs.showDetails(view.getScene().getWindow(), selected);
            }
        });
        view.onRefresh(this::refresh);
    }

    public void refresh() {
        if (!busy) {
            reload(null, "Courses are up to date.");
        }
    }

    private void reload(Long selectId, String message) {
        view.showStatus("Loading saved courses…", false);
        run(service::findAll, courses -> {
            view.showCourses(courses, selectId);
            countChanged.accept(courses.size());
            view.showStatus(message, false);
        }, failure -> {
            countChanged.accept(null);
            view.showStatus(userMessage(failure) + " Use Refresh to try again.", true);
        });
    }

    private void edit(Course existing) {
        if (busy) {
            return;
        }
        CourseFormDialog dialog = new CourseFormDialog(view.getScene().getWindow(), existing);
        dialog.onSave(input -> {
            dialog.setBusy(true);
            run(() -> existing == null ? service.create(input) : service.update(existing.id(), input), saved -> {
                dialog.setBusy(false);
                dialog.close();
                reload(saved.id(), saved.courseCode() + " saved.");
            }, failure -> {
                dialog.setBusy(false);
                dialog.showError(userMessage(failure));
            });
        });
        dialog.show();
    }

    private void deleteSelected() {
        Course selected = view.selectedCourse();
        if (busy || selected == null || !CourseDialogs.confirmDelete(view.getScene().getWindow(), selected)) {
            return;
        }
        run(() -> {
            service.delete(selected.id());
            return selected;
        }, removed -> reload(null, removed.courseCode() + " deleted."),
                failure -> view.showStatus(userMessage(failure), true));
    }

    private <T> void run(Callable<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        busy = true;
        view.setBusy(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            busy = false;
            view.setBusy(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy = false;
            view.setBusy(false);
            failure.accept(task.getException());
        });
        worker.execute(task);
    }

    private static String userMessage(Throwable failure) {
        if (failure instanceof CourseValidationException) {
            return failure.getMessage();
        }
        LOGGER.log(Level.SEVERE, "Course operation failed", failure);
        return failure instanceof CourseStorageException ? failure.getMessage()
                : "The course operation could not be completed. See the launch log for details.";
    }

    @Override
    public void close() {
        worker.shutdown();
    }
}
