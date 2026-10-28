package com.professoros.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.professoros.excel.StudentColumns
import com.professoros.model.StudentImportRow
import com.professoros.pdf.PdfKind

@Composable internal fun StudentImportOverlay(c:WorkspaceController,e:StudentImportEditor) {
    Modal("Import students from Excel",c::closeEditor,wide=true,footer={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(c.busy)Copy("Working… Please wait; nothing else can be submitted.",13,Ink.violet)
            c.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("document-error"))}
            e.result?.let {Copy("$it · Import committed.",13,Ink.violet,modifier=Modifier.testTag("import-result"))}
            Toolbar {
                Action("Preview rows",enabled=!c.busy&&e.sheet!=null,modifier=Modifier.testTag("preview-import")){c.previewStudentImport()}
                Action("Confirm valid rows",variant=ActionVariant.SUCCESS,enabled=!c.busy&&(e.reviewed?.actionable()?:0)>0,modifier=Modifier.testTag("confirm-import")){c.confirmStudentImport()}
            }
        }
    }) {
        CompositionLocalProvider(LocalControlsEnabled provides !c.busy) {
            Copy(e.destination,14,Ink.violet)
            Copy("Existing students are never overwritten. Nothing is saved until you confirm reviewed rows.",13,Ink.muted)
            Action("Choose .xlsx workbook…",enabled=!c.busy,modifier=Modifier.testTag("load-workbook")){c.loadStudentWorkbook()}
            if(e.filename.isNotEmpty())Copy(e.filename,12,Ink.muted)
            val book=e.workbook
            if(book!=null) {
                Select("Sheet",e.sheetIndex.toString(),book.sheets().mapIndexed {i,s->Choice(i.toString(),s.name())},{e.selectSheet(it.toInt())},"import-sheet")
                Select("Header row",e.header.toString(),e.sheet!!.rows().map {Choice(it.number().toString(),"${it.number()} · ${it.cells().take(3).joinToString(" / "){cell->cell.text()}}")},{e.selectHeader(it.toInt())},"import-header")
                val header=e.sheet!!.rows().find {it.number()==e.header}
                val columns=listOf(Choice("-1","Not mapped"))+(header?.cells()?.mapIndexed {i,v->Choice(i.toString(),"${i+1} · ${v.text()}")}.orEmpty())
                StudentColumns.Field.entries.chunked(2).forEach {fields->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    fields.forEach {field->Column(Modifier.weight(1f)){Select(field.toString(),(e.mapping[field]?:-1).toString(),columns,{e.map(field,it.toInt())},"map-${field.name}")}}
                }}
            }
            Copy("Map Student ID and Full name OR First/Last name. Full names split at the first space. Arabic digits normalize to 0–9. Formula cells are rejected.",12,Ink.muted)
            Copy("IDs: use Text cells before entering IDs. Formatting cannot restore digits Excel already lost. Review Numeric ID warnings against the original roster.",12,Ink.muted)
            e.reviewed?.let {preview->
                Copy("${preview.actionable()} actionable · ${preview.rows().count {it.action()==StudentImportRow.Action.SKIP}} skipped · ${preview.rows().count {it.action()==StudentImportRow.Action.ERROR}} failed validation",13,Ink.violet,modifier=Modifier.testTag("import-counts"))
                RecordTable(preview.rows(),listOf(TableColumn("Row",.5f){it:StudentImportRow->it.rowNumber().toString()},TableColumn("Student ID"){it.input().studentNumber()},TableColumn("Name",2f){it.input().firstName()+" "+it.input().lastName()},TableColumn("Section",.7f){it.section()},TableColumn("Action"){it.action().name}),{it.rowNumber().toLong()},e.selected,{e.selected=it.rowNumber().toLong()},{e.selected=it.rowNumber().toLong()},"import-rows")
                preview.rows().find {it.rowNumber().toLong()==e.selected}?.let {
                    Copy("Row ${it.rowNumber()} · ${it.input().email()}\n${it.message()}",13,Ink.violet,modifier=Modifier.testTag("import-row-detail"))
                }?:Copy("Select a row to review its full validation details and email.",12,Ink.muted)
            }
        }
    }
}

@Composable internal fun PdfExportOverlay(c:WorkspaceController,e:PdfExportEditor) {
    Modal("Exam PDF · saved snapshots",c::closeEditor,wide=true,footer={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Copy(if(c.busy)"Working… Rendering or saving document." else e.message,13,Ink.muted,modifier=Modifier.testTag("pdf-status"))
            c.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("document-error"))}
            Toolbar {
                Action("Generate preview",variant=ActionVariant.PRIMARY,enabled=!c.busy,modifier=Modifier.testTag("render-pdf")){c.renderPdf()}
                Action("Save PDF as…",variant=ActionVariant.SUCCESS,enabled=!c.busy&&e.rendered!=null&&(!e.source.exam().marksDiffer()||e.acknowledged),modifier=Modifier.testTag("save-pdf")){c.savePdf()}
                Action("Previous",enabled=!c.busy&&e.page>0,modifier=Modifier.testTag("pdf-previous")){c.turnPdf(-1)}
                Copy("${if(e.rendered==null)0 else e.page+1} / ${e.rendered?.pageCount()?:0}",12,Ink.muted)
                Action("Next",enabled=!c.busy&&e.page+1<(e.rendered?.pageCount()?:0),modifier=Modifier.testTag("pdf-next")){c.turnPdf(1)}
            }
        }
    }) {
        CompositionLocalProvider(LocalControlsEnabled provides !c.busy) {
            Copy(e.source.exam().title()+" · "+e.source.courseLabel(),14,Ink.violet)
            Input("University *",e.university,{e.university=it;e.invalidate()},tag="pdf-university")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Input("Department *",e.department,{e.department=it;e.invalidate()},Modifier.weight(1f),tag="pdf-department")
                Input("Professor *",e.professor,{e.professor=it;e.invalidate()},Modifier.weight(1f),tag="pdf-professor")
            }
            Input("Logo path (optional)",e.logo,{e.logo=it;e.invalidate()},tag="pdf-logo")
            Action("Choose logo…",enabled=!c.busy){c.task({chooseDocument("Choose university logo",extensions=listOf("png","jpg","jpeg"))}) {if(it!=null){e.logo=it.toString();e.invalidate()}}}
            Select("Document",e.kind.name,PdfKind.entries.map {Choice(it.name,it.toString())},{e.kind=PdfKind.valueOf(it);e.invalidate()},"pdf-kind")
            Copy("Calculated ${e.source.exam().calculatedMarks()} / intended ${e.source.exam().intendedMarks()} marks",13,Ink.violet)
            if(e.source.exam().marksDiffer()) {
                Copy("Warning: totals differ. Both totals will be printed; exam content is never adjusted.",13,Ink.violet)
                Action(if(e.acknowledged)"✓ Mark mismatch acknowledged" else "Acknowledge mark mismatch",variant=ActionVariant.WARNING,enabled=!c.busy,modifier=Modifier.testTag("pdf-acknowledge")){e.acknowledged=!e.acknowledged}
            }
            val bitmap=remember(e.png){e.png?.let {org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()}}
            if(bitmap!=null)Image(bitmap,"${e.kind} preview page ${e.page+1}",Modifier.fillMaxWidth().heightIn(max=650.dp).testTag("pdf-image"))
            else Copy("Generate a preview to review wrapping, images and page breaks before saving.",14,Ink.muted)
        }
    }
}
