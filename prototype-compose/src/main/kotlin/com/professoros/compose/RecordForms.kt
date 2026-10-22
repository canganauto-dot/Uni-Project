package com.professoros.compose

import androidx.compose.runtime.*
import com.professoros.model.*
import java.time.LocalDate

data class Choice(val value: String, val label: String)
data class Field(val key: String, val label: String, val initial: String = "", val multiline: Boolean = false, val choices: List<Choice>? = null)
class RecordForm(val title: String, val fields: List<Field>, private val persist: (RecordForm) -> Unit) {
    val values = mutableStateMapOf<String,String>().apply { fields.forEach { put(it.key,it.initial) } }
    operator fun get(key:String) = values[key].orEmpty()
    fun number(key:String) = get(key).trim().toIntOrNull()
    fun id(key:String) = get(key).toLongOrNull() ?: 0L
    fun date(key:String): LocalDate? = try { LocalDate.parse(get(key).trim()) } catch (_: java.time.format.DateTimeParseException) { null }
    val dirty get() = fields.any { get(it.key)!=it.initial }
    fun save() = persist(this)
    fun snapshot() = RecordForm(title,fields.map {it.copy(initial=get(it.key))},persist)
}
internal fun <E:Enum<E>> options(entries: List<E>) = entries.map { Choice(it.name,it.name.replace('_',' ')) }
internal fun Catalog.courseChoices() = courses.map { Choice(it.id().toString(),it.courseCode()+" — "+it.courseName()) }

internal fun WorkspaceController.editCourse(existing:Course?=null) {
    form=RecordForm(if(existing==null)"New course" else "Edit course",listOf(
        Field("code","Course code *",existing?.courseCode().orEmpty()),Field("name","Course name *",existing?.courseName().orEmpty()),
        Field("department","Department",existing?.department().orEmpty()),Field("credits","Credit hours *",existing?.creditHours()?.toString()?:"3"),
        Field("description","Description",existing?.description().orEmpty(),true))) { f ->
        val input=CourseInput(f["code"],f["name"],f["department"],f.number("credits"),f["description"])
        if(existing==null)services.courses.create(input) else services.courses.update(existing.id(),input)
    }
}
internal fun WorkspaceController.editStudent(existing:Student?=null) {
    form=RecordForm(if(existing==null)"New student" else "Edit student",listOf(
        Field("number","Student number *",existing?.studentNumber().orEmpty()),Field("first","First name *",existing?.firstName().orEmpty()),
        Field("last","Last name *",existing?.lastName().orEmpty()),Field("email","Email",existing?.email().orEmpty()),Field("notes","Notes",existing?.notes().orEmpty(),true))) { f ->
        val input=StudentInput(f["number"],f["first"],f["last"],f["email"],f["notes"])
        if(existing==null)services.academic.students().create(input) else services.academic.students().update(existing.id(),input)
    }
}
internal fun WorkspaceController.editTerm(existing:AcademicTerm?=null) {
    form=RecordForm(if(existing==null)"New academic term" else "Edit academic term",listOf(
        Field("name","Term name *",existing?.name().orEmpty()),Field("year","Academic year *",existing?.academicYear().orEmpty()),
        Field("start","Start date * (yyyy-MM-dd)",existing?.startDate()?.toString().orEmpty()),Field("end","End date * (yyyy-MM-dd)",existing?.endDate()?.toString().orEmpty()),
        Field("status","Status",existing?.status()?.name?:"PLANNED",choices=options(AcademicStatus.entries)))) { f ->
        val input=AcademicTermInput(f["name"],f["year"],f.date("start"),f.date("end"),AcademicStatus.valueOf(f["status"]))
        if(existing==null)services.academic.terms().create(input) else services.academic.terms().update(existing.id(),input)
    }
}
internal fun WorkspaceController.editOffering(existing:CourseOffering?=null,courseId:Long?=null) {
    form=RecordForm(if(existing==null)"New course offering" else "Edit course offering",listOf(
        Field("course","Course *",(existing?.courseId()?:courseId)?.toString().orEmpty(),choices=catalog.courseChoices()),
        Field("term","Academic term *",existing?.academicTermId()?.toString().orEmpty(),choices=catalog.terms.map {Choice(it.id().toString(),it.label())}),
        Field("section","Section *",existing?.section().orEmpty()),Field("status","Status",existing?.status()?.name?:"PLANNED",choices=options(AcademicStatus.entries)),
        Field("notes","Notes / meeting schedule",existing?.notes().orEmpty(),true))) { f ->
        val input=CourseOfferingInput(f.id("course"),f.id("term"),f["section"],f["notes"],AcademicStatus.valueOf(f["status"]))
        if(existing==null)services.academic.offerings().create(input) else services.academic.offerings().update(existing.id(),input)
    }
}
internal fun WorkspaceController.enrollStudent() {
    val id=offeringId ?: return
    form=RecordForm("Enroll existing student",listOf(Field("student","Student *",choices=catalog.students.map {Choice(it.id().toString(),it.toString())}))) {
        services.academic.enrollments().enroll(it.id("student"),id)
    }
}
