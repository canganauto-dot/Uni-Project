# Compose desktop evaluation

A separate, native Kotlin/JVM dashboard for evaluating Professor OS's obsidian, charcoal and muted-violet visual direction. The JavaFX workspace remains the complete application. This prototype provides a real saved-data overview, teaching-context selection, recent/upcoming activity and a JavaFX launch action. It does not implement replacement CRUD screens.

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
- `Main.kt` owns the window/session, background loading and errors. `Dashboard.kt` coordinates the shell and navigation; `DesignSystem.kt` holds tokens, typography, original icons and controls. `OverviewPanels.kt` renders real-data cards and `OfferingPicker.kt` shares searchable selection between the top bar and Teaching context page.
- Default storage is the same root `data/professor-os.db`. `PROFESSOR_OS_DATA_DIR` selects another directory exactly as in JavaFX. Startup uses the existing migration runner with checksum verification; no migrations were introduced or altered.
- Dashboard reads do not write academic records. Fixtures exist only in tests. The first saved offering is initially selected; selection stays in this prototype session. Refresh reloads saved records. JavaFX has its own existing session selection.
- **Open full workspace** starts the unchanged JavaFX application in a separate JVM so toolkit lifecycle ownership stays independent. It inherits the data-directory environment. This development handoff emits JavaFX's existing unnamed-module warning; the normal Maven JavaFX launcher remains available.

## Visuals, fonts and motion

Compose Multiplatform Desktop 1.10.3, Kotlin/compiler plugin 2.3.20 and Gradle 9.3.1 are pinned. The independent Gradle project allows evaluation without converting the established Maven application. Custom Foundation components, Canvas icons, quiet borders and tonal panels replace the oversized hero and decorative effects. Teaching focus, library counts, upcoming activity and saved attendance have distinct hierarchies. No Material component layout or additional icon/animation package is required. The header uses a subtle alpha surface; there is no platform blur dependency. Native window controls remain intact, with a compact Professor OS title and original icon.

Bundled Manrope and IBM Plex Sans Arabic are distributed unmodified under SIL OFL 1.1. See [font provenance and licenses](src/main/resources/fonts/README.md). Explicit variable-font weight axes select Manrope 400/500/600/700; leaving its default axis selected made text too thin. Arabic labels and search input use IBM Plex Sans Arabic. Labels use content-based text direction. This is Arabic shaping/bidi support within an English shell, not a fully localized or mirrored RTL application.

Motion cycles **System → Reduced → Full**. The choice is saved in Java Preferences under `com/professoros/compose-prototype` (Windows user preferences registry, independent of the database and JavaFX theme). System reads Windows `SystemParameters.ClientAreaAnimation` in a bounded background process; unknown/failed detection and other platforms default to reduced motion. `PROFESSOR_OS_REDUCED_MOTION=1` can supply the System preference explicitly. Full is an explicit user override. Unfocused windows reduce motion. A critically damped spring (stiffness 420) guides selection, hover and changed-value counters/progress; page reveals use a small 12-pixel slide with short fades. Reduced snaps transitions while preserving focus/hover feedback and session state. There is no ambient animation loop: both modes remain still when idle. Counters animate only when saved values change, without inventing an initial count-up from zero.

The default window is 1360×860 and its minimum is 880×660. Below 1050 logical pixels the sidebar becomes icon-only; below 1120 the cards stack. Content scrolls vertically and is capped at 1340 pixels on larger windows. Theme changes in JavaFX remain independent; this evaluation intentionally has one dark identity.

Ctrl+K (Command+K on macOS) opens the teaching-context picker; Ctrl/Command+R refreshes saved data. Search covers course name/code, term, year and section. Up/Down moves the selection, Enter selects, and Escape dismisses the popup. Buttons support Tab/Enter and visible focus borders. The macOS shortcuts are implemented but have not been verified on macOS.

For repeatable native size checks, optional development properties set the initial window size: `run -PpreviewWidth=880 -PpreviewHeight=660`. They do not persist or affect academic data.

## Verification

```powershell
cmd /d /c ".\prototype-compose\gradlew.bat -p prototype-compose test"
```

Eight tests cover direct Java persistence/reconstruction, unchanged database bytes on reads, real empty states, motion policy, bundled licenses, text contrast, keyboard activation, searchable offering selection, popup dismissal, responsive rendering, intermediate/settled spring positions and idle pixel stability in both motion modes. Rendering uses the pinned Compose test API; test-only internal/experimental opt-ins are isolated from production code. Synthetic records use temporary databases.

Snapshots in ignored `build/screenshots/` include `refined-overview-*` renderer captures at 1360×860, 900×720 and 1700×1050, `refined-minimum-content.png` at 864×621, Arabic context labels and an empty workspace. Actual running Windows captures include `refined-native-1360.png`, `refined-native-880.png`, `refined-native-880-scrolled.png`, `refined-native-maximized.png`, `refined-native-picker.png` and navigation start/settled frames. Native screenshots use the existing database; the blue pointer halo belongs to the capture tool, not the application. `render-observation.txt` measures warmed screenshot/readback time, **not FPS or a GPU benchmark**.

The existing Maven `-DforkCount=0 -Pui-smoke clean verify` workflow also passed all 199 tests. See PROJECT_NOTES for the final native verification and data-integrity evidence.

Remaining evaluation: multi-monitor/high-DPI behavior, other operating systems, comprehensive screen-reader support, fully localized RTL layout, native edge-drag behavior and sustained performance profiling. Native minimum-size launch, scrolling and maximization were checked. The OS controls title-bar appearance. Windows automation exposed only the outer Compose window to UI Automation; semantic tests and injected keyboard checks are not a screen-reader or physical-keyboard certification. No installer or production packaging was added.
