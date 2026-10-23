package com.professoros.compose

import androidx.compose.runtime.*
import com.professoros.model.*
import com.professoros.service.AttendanceDraft
import com.professoros.service.AttendanceCalculator

/** Observable UI adapter; the existing Java draft owns marks and null/unmarked semantics. */
class AttendanceEditor(val book:AttendanceBook,previousLecture:Long?=null) {
    var lectureId by mutableStateOf(book.lectures().find {it.id()==previousLecture}?.id()?:book.lectures().firstOrNull()?.id());private set
    private var draft=lectureId?.let {AttendanceDraft(book,it)}
    private var revision by mutableStateOf(0)
    val lecture get()=book.lectures().find {it.id()==lectureId}
    val readOnly get()=lecture==null || lecture?.status()==LectureStatus.CANCELLED
    val dirty get():Boolean {revision;return draft?.dirty()==true}
    fun value(enrollment:Long):AttendanceStatus? {revision;return draft?.value(enrollment)}
    fun set(enrollment:Long,status:AttendanceStatus?) {if(!readOnly){draft?.set(enrollment,status);revision++}}
    fun changes()=draft?.changes().orEmpty()
    fun discard(){draft=lectureId?.let {AttendanceDraft(book,it)};revision++}
    fun select(id:Long) {lectureId=book.lectures().first {it.id()==id}.id();discard()}
    fun history(row:EnrolledStudent):String = buildString {
        append(AttendanceCalculator.student(book,row.enrollment().id()).display())
        append("\n\nSaved records only. Cancelled lectures are excluded from percentages.\n\n")
        book.lectures().forEach {lecture->
            append("${lecture.plannedDate()} · ${lecture.lectureNumber()}. ${lecture.title()} · ${attendanceLabel(book.status(row.enrollment().id(),lecture.id()))}")
            if(lecture.status()==LectureStatus.CANCELLED)append(" (cancelled)")
            append('\n')
        }
    }
}
internal fun attendanceLabel(status:AttendanceStatus?)=status?.name?.lowercase()?.replaceFirstChar {it.uppercase()}?:"Unmarked"
internal val attendanceChoices=listOf<AttendanceStatus?>(AttendanceStatus.PRESENT,AttendanceStatus.ABSENT,AttendanceStatus.LATE,AttendanceStatus.EXCUSED,null)

internal fun WorkspaceController.markAttendanceRoster(status:AttendanceStatus?) {
    val editor=attendance?:return
    if(busy||editor.readOnly)return
    confirm("Mark entire roster?","Apply ${attendanceLabel(status)} to all ${editor.book.students().size} enrolled students, including hidden and withdrawn rows?\nChanges are staged until Save attendance. Unmarked clears saved marks when saved.") {
        editor.book.students().forEach {editor.set(it.enrollment().id(),status)}
    }
}
