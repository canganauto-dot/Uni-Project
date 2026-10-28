package com.professoros.compose

import androidx.compose.runtime.*
import com.professoros.model.*
import com.professoros.service.*
import java.time.LocalDate

/** Observable adapter around the existing Java draft; no grading arithmetic lives in Compose. */
class GradebookEditor(val book:Gradebook) {
    private var draft=GradebookDraft(book)
    val selected=mutableStateListOf<Long>()
    var bulkAssessment by mutableStateOf(book.assessments().firstOrNull()?.id()?.toString().orEmpty())
    var bulkScore by mutableStateOf("")
    var bulkOpen by mutableStateOf(false)
    private var revision by mutableStateOf(0)
    val dirty get():Boolean { revision;return draft.dirty() }
    fun value(key:GradeKey):String {revision;return draft.value(key)}
    fun set(key:GradeKey,value:String) {draft.set(key,value);revision++}
    fun changes()=draft.changes()
    fun discard(){draft=GradebookDraft(book);revision++}
    // Recalculate only when the draft changes, not on search, focus, tab or hover updates.
    private val calculatedSummary by derivedStateOf {
        revision
        calculateSummary()
    }
    fun summary():GradeSummary = calculatedSummary
    private fun calculateSummary():GradeSummary {
        return try {
            val scores=draft.scores()
            GradeSummary(book.students().associate {it.enrollment().id() to GradeCalculator.calculate(book,it.enrollment().id(),scores)},GradeCalculator.statistics(book,scores),null)
        } catch(failure:DomainValidationException) {GradeSummary(emptyMap(),"Fix invalid scores before calculating or saving.",failure.message)}
    }
}
data class GradeSummary(val results:Map<Long,GradeResult>,val statistics:String,val error:String?)
internal fun WorkspaceController.gradeConfigurable():Boolean {
    if(busy || gradebook==null)return false
    if(gradebook!!.dirty){error="Save or discard grade edits before exporting or changing assessment settings.";return false}
    error=null;return true
}
internal fun WorkspaceController.editGradeCategory(existing:AssessmentCategory?=null) {
    if(!gradeConfigurable())return
    val id=gradebook!!.book.offeringId()
    form=RecordForm(if(existing==null)"New assessment category" else "Edit category",listOf(
        Field("name","Category name *",existing?.name().orEmpty()),Field("weight","Weight % *",existing?.weight()?.stripTrailingZeros()?.toPlainString().orEmpty()))) {
        services.academic.grades().saveCategory(id,existing?.id(),it["name"],GradebookService.number(it["weight"],"Weight"))
    }
}
internal fun WorkspaceController.editAssessment(existing:Assessment?=null) {
    if(!gradeConfigurable())return
    val book=gradebook!!.book
    form=RecordForm(if(existing==null)"New assessment" else "Edit assessment",listOf(
        Field("title","Assessment title *",existing?.title().orEmpty()),
        Field("category","Category *",(existing?.categoryId()?:book.categories().firstOrNull()?.id())?.toString().orEmpty(),choices=book.categories().map {Choice(it.id().toString(),it.name())}),
        Field("maximum","Maximum marks *",existing?.maximumMarks()?.toPlainString()?:"100"),
        Field("date","Date * (yyyy-MM-dd)",existing?.date()?.toString()?:LocalDate.now().toString()))) {
        services.academic.grades().saveAssessment(book.offeringId(),existing?.id(),it.id("category"),it["title"],GradebookService.number(it["maximum"],"Maximum marks"),it.date("date"))
    }
}
internal fun WorkspaceController.editGradingScale() {
    if(!gradeConfigurable())return
    val book=gradebook!!.book;val scale=book.scale()
    form=RecordForm("Grading scale · inclusive minimum percentages",listOf(
        Field("a","A minimum %",scale.a().stripTrailingZeros().toPlainString()),Field("b","B minimum %",scale.b().stripTrailingZeros().toPlainString()),
        Field("c","C minimum %",scale.c().stripTrailingZeros().toPlainString()),Field("d","D minimum %",scale.d().stripTrailingZeros().toPlainString()),Field("pass","E / Pass minimum %",scale.pass().stripTrailingZeros().toPlainString()))) {f->
        fun n(key:String)=GradebookService.number(f[key],"Threshold")
        services.academic.grades().saveScale(book.offeringId(),GradingScale(n("a"),n("b"),n("c"),n("d"),n("pass")))
    }
}
internal fun WorkspaceController.applySelectedGrades() {
    val editor=gradebook?:return
    if(busy || editor.selected.isEmpty())return
    try {
        val assessment=editor.book.assessments().firstOrNull {it.id().toString()==editor.bulkAssessment}?:throw DomainValidationException("Choose an assessment.")
        val value=editor.bulkScore
        if(value.isBlank())throw DomainValidationException("Enter a score; edit individual cells to clear grades.")
        GradebookService.validateScore(value,assessment)
        val keys=editor.book.students().filter {it.enrollment().id() in editor.selected}.map {GradeKey(it.enrollment().id(),assessment.id())}
        val apply={keys.forEach {editor.set(it,value)};editor.bulkScore="";error=null}
        if(keys.any {editor.value(it).isNotBlank()})confirm("Overwrite selected grades?","Apply $value to ${keys.size} selected students for ${assessment.title()}? Existing grades will be replaced in the draft. Save grades to persist.",apply) else apply()
    }catch(f:DomainValidationException){error=f.message}
}
