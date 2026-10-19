# Professor OS appearance foundation

The visual direction is quiet and editorial: warm paper surfaces in Light, charcoal surfaces in Dark, and an indigo academic identity. Existing page layouts and academic operations remain intact.

## Using themes

- The top-bar appearance button cycles Light → Dark → System. Its label shows the saved mode.
- Settings → Appearance offers the same three choices directly.
- System follows JavaFX's operating-system color-scheme property and reacts to changes. If the platform supplies no dark preference, the fallback is Light.
- Preferences live in `appearance.properties` beside `professor-os.db`, respecting `PROFESSOR_OS_DATA_DIR`. A write failure preserves the current mode and reports an error in Settings and the toggle tooltip. Missing/invalid preferences fall back to System; invalid reads are logged.

## Extending the visual language

`css/palette.css` contains semantic color tokens for both modes: canvas/surface, primary/muted text, accent, hover/selection, borders, errors and chart series. Never add a feature-specific light/dark conditional to a controller.

`css/theme.css` centralizes typography, geometry and component styles. JavaFX supports looked-up colors but not browser CSS custom properties or `var()`. Therefore spacing, radii and typography use shared component rules and a documented scale rather than unsupported numeric variables. Body text is 13px Segoe UI (Noto Sans fallback); supporting text 11px, section headings 15px, page headings 28px. Geometry uses 4/8/12/16/20/24/30px spacing and 6/8/10px corner radii. Existing Java layout gaps are preserved in this foundation phase.

Use `primary-button` for the principal action, `secondary-button` for ordinary actions, `danger-button` for destructive actions, and `panel`, `muted`, `small-muted`, `error-message` and existing heading classes for content. Focus uses a visible accent outline. Table selections retain readable foregrounds. Inputs, dropdowns, dates, tabs, dialogs, tooltips and chart specimens share the palette; charts currently define four semantic series colors. No new chart or analytics screen was added.

## Ownership and lifecycle

Each MainView owns a presentation-only ThemeManager. It changes style classes on existing scene roots and owned dialog/popup roots; it never calls navigation or reloads services. This preserves form text, caret position, selections and unsaved grade drafts. Window and OS-preference listeners are released when MainController closes. ThemeMode and ThemePreferences remain independent of JavaFX; ThemeManager stays in the presentation layer. No migration, dependency-injection framework or external theme library is needed.

The OS color-scheme API is documented by [OpenJFX 25](https://openjfx.io/javadoc/25/javafx.graphics/javafx/application/Platform.Preferences.html). Tests inject an observable color scheme to verify live System changes and explicit overrides without changing Windows settings.

## Verification boundaries

Preference tests cover persisted modes, invalid settings, write failures and mode resolution. Semantic text/background pairs are checked against a 4.5:1 contrast threshold. Desktop tests check synchronized controls, unchanged Settings view identity, open-form text/caret preservation, unsaved grade cells, owned dialogs/tooltips, charts and both palette layouts at 1360×900 and 1100×740. Screenshots are generated under `target/ui-smoke/theme-*.png`.

The minimum-theme specimens resize/layout the existing root; the broader desktop smoke test additionally performs a native minimum-window resize. Physical keyboard and screen-reader audits, mixed-DPI/multiple-monitor testing, other OS platforms and an actual OS theme change remain manual checks. Native title bars and Windows file pickers follow the OS and are outside JavaFX CSS. This is not a Windows High Contrast implementation or a full accessibility certification. Page-specific redesigns and font-size preferences remain future work.
