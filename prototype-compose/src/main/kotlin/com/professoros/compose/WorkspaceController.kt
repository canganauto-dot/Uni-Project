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
    private data class Loaded(val catalog: Catalog, val roster: List<EnrolledStudent>, val lectures: LectureOverview?, val questions: List<Question>, val exams: List<Exam>)
    private fun load(id: Long?): Loaded {
        val catalog = services.catalog()
        val valid = id?.takeIf { value -> catalog.offerings.any { it.offering().id() == value } }
        val a = services.academic
        return Loaded(catalog, valid?.let { a.enrollments().findStudentsForOffering(it) }.orEmpty(),
            valid?.let { a.lectures().overview(it) }, a.questions().findAll(), valid?.let { a.exams().findByOffering(it) }.orEmpty())
    }
    private fun apply(data: Loaded) {
        catalog=data.catalog; roster=data.roster; lectures=data.lectures; questions=data.questions; exams=data.exams
        if(catalog.offerings.none { it.offering().id()==offeringId }) offeringId=null
    }
    fun refresh() = task({ services.initialize(); load(offeringId) }, ::apply)
    fun select(id: Long?) { if(busy) return; offeringId=id; refresh() }
    fun mutate(operation: () -> Unit) = task({ operation(); load(offeringId) }) { apply(it); form=null; examDraft=null; changed() }
    fun saveForm() { val current=form?.snapshot() ?: return; mutate { current.save() } }
    fun guard(action: () -> Unit) {
        if(busy) return
        if(form?.dirty==true || examDraft?.dirty==true) confirmation=Confirmation("Discard unsaved changes?", "Your changes have not been saved. Keep editing or discard them to continue.") {
            form=null; examDraft=null; error=null; action()
        } else { form=null; examDraft=null; error=null; action() }
    }
    fun closeEditor() = guard { detail=null }
    fun confirm(title: String, message: String, action: () -> Unit) { if(!busy) confirmation=Confirmation(title,message,action) }
    fun inspect(title: String, operation: () -> String) = task(operation) { detail=Detail(title,it) }
}
