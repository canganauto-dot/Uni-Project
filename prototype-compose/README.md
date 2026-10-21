# Compose desktop evaluation

A separate, native Kotlin/JVM dashboard for evaluating Professor OS's midnight/navy/violet visual direction. The JavaFX workspace remains the complete application. This prototype provides a real saved-data overview, teaching-context selection, recent/upcoming activity and a JavaFX launch action. It does not implement replacement CRUD screens.

## Launch

From the repository root, with JDK 25 in `JAVA_HOME`:

```powershell
.\prototype-compose\gradlew.bat -p prototype-compose run
```

On the original Windows machine, bypass its existing shell AutoRun setting:

```powershell
$env:JAVA_HOME = 'C:\Users\HP\.jdks\openjdk-25.0.1'
$env:MAVEN_OPTS = '--enable-native-access=ALL-UNNAMED'
cmd /d /c ".\prototype-compose\gradlew.bat -p prototype-compose run"
```

Linux/macOS equivalent: `./prototype-compose/gradlew -p prototype-compose run` (not verified on those platforms). First build downloads Gradle and dependencies; fonts and runtime data are local. Close the running development application before rebuilding: the development classpath uses build outputs directly and is not a hot-reload setup.

`prepareBackend` packages the existing Maven application and copies its runtime dependencies. No Maven install, backend server, Node.js, WebView or Electron is involved. Do not run Maven `clean` concurrently with Gradle tasks because both need the Maven output. The Gradle wrapper distribution has a pinned SHA-256 checksum.

## Data and architecture

- `DashboardBridge` directly calls existing Java `CourseService`, `AcademicServices`, `DashboardService` and `AttendanceCalculator`. It contains no SQL or duplicated grading/progress calculations.
- `Main.kt` owns the window/session, background loading and errors. `Dashboard.kt` contains Compose presentation and shared visual primitives.
- Default storage is the same root `data/professor-os.db`. `PROFESSOR_OS_DATA_DIR` selects another directory exactly as in JavaFX. Startup uses the existing migration runner with checksum verification; no migrations were introduced or altered.
- Dashboard reads do not write academic records. Fixtures exist only in tests. The first saved offering is initially selected; selection stays in this prototype session. Refresh reloads saved records. JavaFX has its own existing session selection.
- **Open JavaFX** starts the unchanged application in a separate JVM so toolkit lifecycle ownership stays independent. It inherits the data-directory environment. This development handoff emits JavaFX's existing unnamed-module warning; the normal Maven JavaFX launcher remains available.

## Visuals, fonts and motion

Compose Multiplatform Desktop 1.10.3, Kotlin/compiler plugin 2.3.20 and Gradle 9.3.1 are pinned. The independent Gradle project allows evaluation without converting the established Maven application. Custom Foundation components, Canvas icons, restrained gradients and layered panels are used; no Material component layout or additional icon/animation package is required.

Bundled Manrope and IBM Plex Sans Arabic are distributed unmodified under SIL OFL 1.1. See [font provenance and licenses](src/main/resources/fonts/README.md). Explicit variable-font weight axes select Manrope 400/600/700; leaving its default axis selected made text too thin. Arabic uses IBM Plex Sans Arabic and content-based text direction. This is Arabic shaping/bidi support within an English shell, not a fully localized or mirrored RTL application.

Motion cycles **System → Reduced → Full**. The choice is saved in Java Preferences under `com/professoros/compose-prototype` (Windows user preferences registry, independent of the database and JavaFX theme). System reads Windows `SystemParameters.ClientAreaAnimation` in a bounded background process; unknown/failed detection and other platforms default to reduced motion. `PROFESSOR_OS_REDUCED_MOTION=1` can supply the System preference explicitly. Full is an explicit user override. Unfocused windows reduce motion. Reduced removes navigation fades, hover transitions, counter/progress transitions and the ambient loop while keeping focus/hover feedback. The ambient opacity is read in a graphics layer, avoiding recomposing the dashboard every frame. Counters animate when saved values change; they do not invent an initial count-up from zero.

The window minimum is 880×660. Below 1100 logical pixels the sidebar becomes icon-only; below 1000 the lower panels stack. Content scrolls vertically. Theme changes in JavaFX remain independent; this evaluation intentionally has one dark identity.

## Verification

```powershell
cmd /d /c ".\prototype-compose\gradlew.bat -p prototype-compose test"
```

Seven tests cover direct Java persistence/reconstruction, unchanged database bytes on reads, real empty states, motion policy, bundled licenses, text contrast, keyboard activation, navigation/offering selection, responsive rendering, full-motion pixel changes and reduced-motion pixel stability. Rendering uses the pinned Compose test API; test-only internal/experimental opt-ins are isolated from production code. Synthetic records use temporary databases.

Snapshots in ignored `build/screenshots/` include 1360×960, 900×720, 864×621 minimum-content and 1700×1050 layouts, Arabic context labels and an empty workspace. These are renderer captures; `native-dashboard.png` and `native-javafx.png` were separately captured from actual running Windows applications against the existing database. `render-observation.txt` measures warmed screenshot/readback time, **not FPS or a GPU benchmark**.

The existing Maven `-DforkCount=0 -Pui-smoke clean verify` workflow also passed all 199 tests. See PROJECT_NOTES for the final native verification and data-integrity evidence.

Remaining evaluation: multi-monitor/high-DPI behavior, other operating systems, comprehensive screen-reader support, fully localized RTL layout, native resize-drag behavior and sustained performance profiling. Windows automation exposed only the outer Compose window to UI Automation; semantic tests and keyboard checks are not a screen-reader certification. No installer or production packaging was added.
