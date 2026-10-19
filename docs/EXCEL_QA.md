# Excel QA checklist

Use synthetic data and an isolated database, never the professor's working database.
The standard build regenerates these ignored review files under `target/excel-qa/`:

- `realistic-roster.xlsx`: 36 bilingual students, six-digit text IDs, optional blank emails, two repeated IDs, a missing ID and an empty row.
- `realistic-gradebook.xlsx`: the imported roster with an Arabic assessment title, one real zero and 35 ungraded scores.
- `qa-data/professor-os.db`: isolated course/offering/enrollment/grade data for the desktop checks below.

## Microsoft Excel — pending manual verification

1. Open `realistic-gradebook.xlsx` in Microsoft Excel. Confirm no repair prompt appears and all three sheets are available.
2. Check Arabic names and the assessment title. Student IDs must display six digits (for example `000001`), with the original string visible in the formula bar.
3. Check one numeric `0.00` score and 35 genuinely empty score cells. Incomplete students must have blank final percentage/letter, not a fabricated final zero. Statistics must show one complete and 35 incomplete results.
4. Check filters, frozen headers/identifiers, scrolling, date/percentage formatting and Print Preview. Do not save over the generated original while inspecting it.

## Native dialogs and keyboard — pending manual verification

Launch the application with `PROFESSOR_OS_DATA_DIR` pointing to `target/excel-qa/qa-data`, using the documented JDK/Maven environment. Build first; another clean build removes this QA database.

1. Students → Import from Excel → Browse. Select `realistic-roster.xlsx` using the native Open dialog. Test Cancel as well: it must leave the previous selection unchanged.
2. Tab through file, sheet/header, mappings and preview controls. Check Shift+Tab, Space/Enter activation, arrow-key sheet/mapping selection and Escape to close an idle dialog. Enter must not unexpectedly commit an unreviewed import.
3. Load and preview the file. The isolated database already contains the 36 valid students, so expect 36 skipped, three validation errors and no actionable rows. Select each error and verify the full explanation is readable. Nothing should overwrite those students.
4. For a fresh global import, use a separate empty QA data directory. Expect 36 created, three failed validation, no duplicate profiles and preserved IDs/names after restarting.
5. In the populated QA database select CS201 / Fall / Section A, open Grades, choose Export to Excel and use the native Save dialog to select a new filename. Test Cancel, Escape and keyboard submission.
6. Try exporting to an existing file. Even if Windows asks about replacement, Professor OS must reject the existing destination. Confirm the original file remains unchanged.
7. Make a temporary grade edit; Export must ask you to save/discard first. Discard the edit and verify the missing/zero distinction remains intact.

## Preparing numeric IDs

Set the Excel ID column to **Text before entering IDs**. For values already entered as numbers, re-enter the complete ID from the original roster after changing the format. Merely switching Number to Text cannot restore removed leading zeros or digits rounded after Excel's 15-digit precision limit.

Numeric zero-padding formats such as `000000` are accepted only when they preserve the underlying integer. Abbreviated/scaled formats (for example `0,`), scientific notation, dates, percentages and other non-digit displays are rejected with recovery guidance. Review every Numeric ID warning. Text IDs remain subject to the existing student identifier rules.

## Automated coverage and local limitations

JUnit verifies the realistic files, transactional import counts, Arabic text, leading zeros, duplicate handling and blank versus zero cells after reopening XLSX through POI. Regression tests reproduce the scaled-number ID corruption and drive-root path failures before their fixes. Desktop smoke checks exercise real JavaFX controls and capture the import/export dialogs, but are not physical-keyboard or native-picker tests.

On 2026-10-19 Microsoft Excel was found and launched. The Windows automation helper opened its native Open dialog but subsequently returned stale control indexes and an incorrect application screenshot after recovery. Native testing was stopped to avoid misdirected input. No workbook-open, repair-free, native-picker end-to-end or physical-keyboard success is claimed. Complete the pending checks above on a reliable interactive desktop.
