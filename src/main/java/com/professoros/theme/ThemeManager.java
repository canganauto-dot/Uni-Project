package com.professoros.theme;

import com.professoros.model.ThemeMode;
import com.professoros.service.ThemePreferences;
import javafx.application.Platform;
import javafx.application.ColorScheme;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.stage.*;
import java.util.*;
import java.util.logging.*;

/** Presentation-only session coordinator: restyles existing roots without replacing any view. */
public final class ThemeManager implements AutoCloseable {
    private final Parent main;
    private final ThemePreferences preferences;
    private final javafx.beans.value.ObservableValue<ColorScheme> systemScheme;
    private final ReadOnlyObjectWrapper<ThemeMode> mode;
    private final ReadOnlyStringWrapper message=new ReadOnlyStringWrapper("");
    private final Set<Parent> roots=Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<javafx.scene.control.PopupControl> popups=Collections.newSetFromMap(new WeakHashMap<>());
    private final ChangeListener<ColorScheme> systemListener=(o,a,b)->apply();
    private final ListChangeListener<Window> windows=change->{ while(change.next()) { if(change.wasAdded()) { change.getAddedSubList().forEach(this::attach); } } };
    public ThemeManager(Parent main,ThemePreferences preferences) {
        this(main,preferences,Platform.getPreferences().colorSchemeProperty());
    }
    public ThemeManager(Parent main,ThemePreferences preferences,javafx.beans.value.ObservableValue<ColorScheme> systemScheme) {
        this.main=main; this.preferences=preferences; this.systemScheme=systemScheme; mode=new ReadOnlyObjectWrapper<>(preferences.load());
        roots.add(main); apply();
        systemScheme.addListener(systemListener);
        Window.getWindows().addListener(windows);
    }
    public ReadOnlyObjectProperty<ThemeMode> modeProperty() { return mode.getReadOnlyProperty(); }
    public ReadOnlyStringProperty messageProperty() { return message.getReadOnlyProperty(); }
    public ThemeMode mode() { return mode.get(); }
    public void select(ThemeMode selected) {
        if(selected==null || selected==mode()) { return; }
        try { preferences.save(selected); mode.set(selected); message.set(""); apply(); }
        catch(java.io.IOException failure) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING,"Cannot save appearance",failure);
            message.set("Appearance could not be saved. Check that the data folder is writable.");
        }
    }
    private boolean belongs(Window window) {
        if(main.getScene()!=null && window==main.getScene().getWindow()) { return true; }
        Window owner=window instanceof Stage s?s.getOwner():window instanceof PopupWindow p?p.getOwnerWindow():null;
        return owner!=null && belongs(owner);
    }
    private void attach(Window window) {
        if(belongs(window) && window.getScene()!=null) {
            if(window instanceof javafx.scene.control.PopupControl popup) { popups.add(popup); }
            Parent root=window.getScene().getRoot(); roots.add(root);
            String css=Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm();
            if(!window.getScene().getStylesheets().contains(css)) { window.getScene().getStylesheets().add(css); }
            apply();
        }
    }
    private void apply() {
        boolean dark=mode().dark(systemScheme.getValue()==ColorScheme.DARK);
        for(Parent root:roots) {
            root.getStyleClass().removeAll("theme-light","theme-dark");
            root.getStyleClass().add(dark?"theme-dark":"theme-light");
        }
        for(var popup:popups) {
            popup.getStyleClass().removeAll("theme-light","theme-dark");
            popup.getStyleClass().add(dark?"theme-dark":"theme-light");
        }
    }
    @Override public void close() { Window.getWindows().removeListener(windows); systemScheme.removeListener(systemListener); roots.clear(); popups.clear(); }
}
