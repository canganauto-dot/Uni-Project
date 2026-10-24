package com.professoros.compose

import androidx.compose.runtime.*
import com.professoros.excel.*
import com.professoros.model.*
import com.professoros.pdf.*
import com.professoros.service.ExamPdfService
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/** Called only by background tasks; native dialogs are owned by the AWT event thread. */
internal fun chooseDocument(title:String,save:Boolean=false,name:String="",extensions:List<String>):Path? {
    var result:Path?=null
    EventQueue.invokeAndWait {
        // Keep the established ownerless AWT dialog: Compose popups use a separate focus hierarchy.
        val picker=FileDialog(null as Frame?,title,if(save)FileDialog.SAVE else FileDialog.LOAD)
        try {
            picker.file=if(name.isNotEmpty())name else extensions.joinToString(";"){"*.$it"}
            picker.setFilenameFilter {_,file->extensions.any {file.endsWith(".$it",true)}}
            picker.isVisible=true
            if(picker.file!=null)result=Path.of(picker.directory,picker.file)
        } finally {picker.dispose()}
    }
    return result
}

class StudentImportEditor(val offeringId:Long?,val destination:String) {
    var filename by mutableStateOf("");private set
    var workbook by mutableStateOf<StudentWorkbook?>(null);private set
    var sheetIndex by mutableStateOf(0);private set
    var header by mutableStateOf(1);private set
    val mapping=mutableStateMapOf<StudentColumns.Field,Int>()
    var reviewed by mutableStateOf<StudentImportPreview?>(null)
    var result by mutableStateOf<StudentImportResult?>(null)
    var selected by mutableStateOf<Long?>(null)
    val sheet get()=workbook?.sheets()?.getOrNull(sheetIndex)
    val dirty get()=workbook!=null && result==null
    fun invalidate(){reviewed=null;result=null;selected=null}
    fun loaded(path:Path,value:StudentWorkbook){filename=path.toString();workbook=value;selectSheet(value.sheets().indexOfFirst {it.rows().isNotEmpty()}.coerceAtLeast(0))}
    fun selectSheet(index:Int){sheetIndex=index;selectHeader(StudentColumns.headerRow(sheet!!))}
    fun selectHeader(number:Int){header=number;mapping.clear();sheet?.rows()?.find {it.number()==number}?.let {mapping.putAll(StudentColumns.detect(it))};invalidate()}
    fun map(field:StudentColumns.Field,column:Int){if(column<0)mapping.remove(field) else mapping[field]=column;invalidate()}
}

class PdfExportEditor(val source:ExamPrintSource) {
    var university by mutableStateOf("")
    var department by mutableStateOf(source.department())
    var professor by mutableStateOf("")
    var logo by mutableStateOf("")
    var kind by mutableStateOf(PdfKind.STUDENT)
    var acknowledged by mutableStateOf(false)
    var rendered by mutableStateOf<RenderedExamPdf?>(null)
    var png by mutableStateOf<ByteArray?>(null)
    var page by mutableStateOf(0)
    var message by mutableStateOf("Enter print details, then generate a preview.")
    var saved by mutableStateOf(false)
    val dirty get()=!saved && (university.isNotBlank()||professor.isNotBlank()||logo.isNotBlank()||department!=source.department()||rendered!=null)
    fun invalidate(){rendered=null;png=null;page=0;saved=false;message="Generate a new preview for these settings."}
    fun profile()=ExamPrintProfile(university,department,professor,logo)
}

internal fun WorkspaceController.pdfService()=ExamPdfService(services.academic.exams(),services.academic.offerings(),services.courses)
internal fun WorkspaceController.openStudentImport(id:Long?=null) {
    if(busy)return
    guard {studentImport=StudentImportEditor(id,if(id==null)"Global student directory" else catalog.offerings.first {it.offering().id()==id}.label())}
}
internal fun WorkspaceController.loadStudentWorkbook(pick:()->Path?={chooseDocument("Import students from Excel",extensions=listOf("xlsx"))}) {
    if(busy)return
    val editor=studentImport?:return
    editor.invalidate()
    task({pick()?.let {it to StudentWorkbookReader().read(it)}}) {it?.let {(path,book)->editor.loaded(path,book)}}
}
internal fun WorkspaceController.previewStudentImport() {
    if(busy)return
    val editor=studentImport?:return;val sheet=editor.sheet?:return
    val mapping=editor.mapping.toMap();val header=editor.header
    editor.invalidate()
    task({services.academic.imports().preview(sheet,header,mapping,editor.offeringId)}) {editor.reviewed=it}
}
internal fun WorkspaceController.confirmStudentImport() {
    val editor=studentImport?:return;val preview=editor.reviewed?:return
    if(busy||preview.actionable()==0L)return
    confirm("Import reviewed valid rows?","${preview.actionable()} students/enrollments will be imported into ${editor.destination}. Invalid rows are excluded; existing student profiles are never overwritten.") {
        var result:StudentImportResult?=null
        mutate(committed={editor.reviewed=null;editor.result=result}) {result=services.academic.imports().confirm(preview)}
    }
}
internal fun WorkspaceController.openPdf(examId:Long) {
    val id=offeringId?:return
    guard {task({pdfService().load(id,examId)}) {pdfExport=PdfExportEditor(it)}}
}
internal fun WorkspaceController.renderPdf() {
    if(busy)return
    val editor=pdfExport?:return;val profile=editor.profile();val kind=editor.kind
    editor.invalidate()
    task({val service=pdfService();val pdf=service.render(editor.source,profile,kind);pdf to service.preview(pdf,0)}) {
        editor.rendered=it.first;editor.png=it.second;editor.message=it.first.warnings().joinToString("\n").ifEmpty {"Preview ready. Save this document or switch to the other document type."}
    }
}
internal fun WorkspaceController.turnPdf(delta:Int) {
    if(busy)return
    val editor=pdfExport?:return;val pdf=editor.rendered?:return;val page=editor.page+delta
    task({pdfService().preview(pdf,page)}) {editor.png=it;editor.page=page}
}
internal fun WorkspaceController.savePdf(pick:(PdfKind)->Path?={kind->chooseDocument("Save PDF as a new file",true,if(kind==PdfKind.STUDENT)"student-exam.pdf" else "answer-key.pdf",listOf("pdf"))}) {
    if(busy)return
    val editor=pdfExport?:return;val pdf=editor.rendered?:return;val acknowledged=editor.acknowledged
    task({pick(pdf.kind())?.also {pdfService().saveAs(pdf,it,acknowledged)}}) {if(it!=null){editor.saved=true;editor.message="${pdf.kind()} saved to $it"}}
}
