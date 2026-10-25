# Professor OS appearance

Compose Desktop is the only UI. The identity uses charcoal and muted violet in Dark, with coordinated neutral/violet surfaces in Light. Manrope and IBM Plex Sans Arabic provide Latin and Arabic typography.

## Using themes

- Settings → Appearance selects Light, Dark or System.
- System uses Compose desktop appearance detection. Cross-platform live OS changes remain a manual verification item.
- The existing Java ThemePreferences service atomically saves `appearance.properties` beside the active database, respecting `PROFESSOR_OS_DATA_DIR`. Missing/invalid values fall back to System; failed reads are logged. Failed writes retain the prior selection and show a concise error.
- Theme changes update composition-local colors without reconstructing controllers, navigating or reloading data. Pending preference saves finish before normal window closure.

## Extending the visual language

`prototype-compose/src/main/kotlin/com/professoros/compose/DesignSystem.kt` defines `Palette`, `LightPalette`, `DarkPalette`, `LocalPalette` and the `Ink` semantic accessors. Use these shared colors and the existing Copy, Action, Input, Select, Panel, Modal and RecordTable components. Do not add screen-specific hardcoded light/dark branches. Focus borders, selected controls, dialogs, tooltips and spreadsheet cells use the same tokens.

The shared palette is provided around both the application shell and WorkspaceOverlays in Main.kt. AppearanceSettings coordinates background preference writes; Java ThemeMode/ThemePreferences have no UI dependencies. No CSS or JavaFX theme manager remains.

Animations have a single fixed spring intensity, short page fades and no ambient loop. There are no animation-intensity or reduced-motion settings, probes or preference logic.

## Verification boundaries

SettingsUiTest checks saved modes, failed writes, retained unsaved form state, system resolution, semantic contrast of at least 4.5:1 and normal/minimum layouts. DashboardUiTest checks keyboard context selection and fixed animation movement/settling. Renderer snapshots are under `prototype-compose/build/screenshots/settings-*.png`. Native Windows verification checked appearance selection and reload after restart at the minimum window size.

Native title bars/file pickers follow the OS. Mixed-DPI, multiple monitors, other operating systems, physical-keyboard and screen-reader certification remain manual evaluation tasks. Arabic shaping is supported within the English shell; this is not complete RTL localization.
