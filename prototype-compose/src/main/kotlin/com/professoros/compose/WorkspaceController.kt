package com.professoros.compose

import androidx.compose.runtime.*
import com.professoros.database.Database
import com.professoros.model.*
import com.professoros.repository.CourseRepository
import com.professoros.service.*
import kotlinx.coroutines.*
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/** Explicit Java service composition, shared with isolated desktop tests. No persistence rules here. */
class WorkspaceServices(path: Path) {
    private val database = Database(path)
    val academic = AcademicServices.create(database)
    val courses = CourseService(CourseRepository(database))
    fun initialize() = database.initialize()
    fun catalog() = Catalog(courses.findAll(), academic.students().findAll(), academic.terms().findAll(), academic.offerings().summaries())
}
data class Catalog(val courses: List<Course> = emptyList(), val students: List<Student> = emptyList(),
    val terms: List<AcademicTerm> = emptyList(), val offerings: List<OfferingSummary> = emptyList())
data class Confirmation(val title: String, val message: String, val proceed: () -> Unit)
data class Detail(val title: String, val text: String)

/** Coordinates IO and editing state. Services remain authoritative for validation and calculations. */
class WorkspaceController(val services: WorkspaceServices, private val scope: CoroutineScope, private val changed: () -> Unit = {}) {
    var catalog by mutableStateOf(Catalog()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var form by mutableStateOf<RecordForm?>(null)
    var detail by mutableStateOf<Detail?>(null)
    var confirmation by mutableStateOf<Confirmation?>(null)
    var offeringId by mutableStateOf<Long?>(null); private set
    var roster by mutableStateOf<List<EnrolledStudent>>(emptyList()); private set
    var lectures by mutableStateOf<LectureOverview?>(null); private set
    var questions by mutableStateOf<List<Question>>(emptyList()); private set
    var exams by mutableStateOf<List<Exam>>(emptyList()); private set
    var examDraft by mutableStateOf<ExamDraft?>(null)
    var questionDraft by mutableStateOf<QuestionDraft?>(null)
    var gradebook by mutableStateOf<GradebookEditor?>(null); private set
    var gradeBulk by mutableStateOf<GradeBulk?>(null)
    var attendance by mutableStateOf<AttendanceEditor?>(null); private set
    private var attendanceLectureId:Long?=null
    val active get() = catalog.offerings.firstOrNull { it.offering().id() == offeringId }

    fun <T> task(operation: () -> T, success: (T) -> Unit = {}) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { val result = withContext(Dispatchers.IO) { operation() }; success(result) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Logger.getLogger("ComposeWorkspace").log(Level.WARNING, "Workspace operation failed", failure)
                error = when (failure) {
                    is DomainValidationException, is CourseValidationException -> failure.message
                    else -> "Could not complete this operation. Your edits are retained. Refresh and try again; see the launch log for details."
                }
            } finally { busy = false }
        }
    }
    private data class Loaded(val catalog: Catalog, val roster: List<EnrolledStudent>, val lectures: LectureOverview?, val questions: List<Question>, val exams: List<Exam>, val grades: Gradebook?, val attendance: AttendanceBook?)
    private fun load(id: Long?): Loaded {
        val catalog = services.catalog()
        val valid = id?.takeIf { value -> catalog.offerings.any { it.offering().id() == value } }
        val a = services.academic
        return Loaded(catalog, valid?.let { a.enrollments().findStudentsForOffering(it) }.orEmpty(),
            valid?.let { a.lectures().overview(it) }, a.questions().findAll(), valid?.let { a.exams().findByOffering(it) }.orEmpty(),valid?.let {a.grades().load(it)},valid?.let {a.attendance().load(it)})
    }
    private fun apply(data: Loaded) {
        catalog=data.catalog; roster=data.roster; lectures=data.lectures; questions=data.questions; exams=data.exams
        if(gradebook?.dirty!=true || gradebook?.book?.offeringId()!=data.grades?.offeringId()) gradebook=data.grades?.let(::GradebookEditor)
        if(attendance?.dirty!=true || attendance?.book?.offeringId()!=data.attendance?.offeringId()) attendance=data.attendance?.let {AttendanceEditor(it,attendance?.lectureId?:attendanceLectureId)}
        if(catalog.offerings.none { it.offering().id()==offeringId }) offeringId=null
    }
    fun refresh() = task({ services.initialize(); load(offeringId) }, ::apply)
    fun select(id: Long?) {
        if(busy)return
        if(id!=offeringId) {
            if(gradebook?.dirty==true || attendance?.dirty==true) {guard {select(id)};return}
            // Never show the previous offering's scores under a new context while loading or after a failed read.
            gradebook=null;attendance=null;attendanceLectureId=null
        }
        offeringId=id;refresh()
    }
    fun mutate(committed: () -> Unit = {}, operation: () -> Unit) = task({
        operation()
        // A committed write must never be reported as an unsaved edit if a subsequent read fails.
        try {load(offeringId)} catch(failure:Exception) {
            Logger.getLogger("ComposeWorkspace").log(Level.WARNING,"Saved successfully but refresh failed",failure);null
        }
    }) {
        committed()
        if(it!=null)apply(it) else error="Saved successfully, but the list could not refresh. Use Refresh; do not create the record again."
        form=null;examDraft=null;questionDraft=null;changed()
    }
    fun saveForm() { val current=form?.snapshot() ?: return; mutate { current.save() } }
    fun guard(includeSheets:Boolean=true, action: () -> Unit) {
        if(busy) return
        fun proceed() {
            form=null; examDraft=null; questionDraft=null; gradeBulk=null; error=null
            if(includeSheets) {gradebook?.discard();attendance?.discard()}
            action()
        }
        if(form?.dirty==true || examDraft?.dirty==true || questionDraft?.dirty==true || gradeBulk?.dirty==true || (includeSheets && (gradebook?.dirty==true || attendance?.dirty==true))) confirmation=Confirmation("Discard unsaved changes?", "Your changes have not been saved. Keep editing or discard them to continue.",::proceed)
        else proceed()
    }
    fun closeEditor() = guard(false) { detail=null }
    fun saveGrades() {
        val editor=gradebook ?: return
        if(!editor.dirty)return
        val entries=editor.changes()
        mutate(committed={gradebook=null}) { services.academic.grades().saveGrades(editor.book.offeringId(),entries) }
    }
    fun chooseAttendanceLecture(id:Long) {
        if(busy || attendance?.lectureId==id)return
        guard {attendance?.select(id);attendanceLectureId=id}
    }
    fun saveAttendance() {
        val editor=attendance?:return
        val lecture=editor.lectureId?:return
        if(!editor.dirty||editor.readOnly)return
        val entries=editor.changes()
        mutate(committed={attendanceLectureId=lecture;attendance=null}) {services.academic.attendance().save(editor.book.offeringId(),lecture,entries)}
    }
    fun confirm(title: String, message: String, action: () -> Unit) { if(!busy) confirmation=Confirmation(title,message,action) }
    fun inspect(title: String, operation: () -> String) = task(operation) { detail=Detail(title,it) }
}
