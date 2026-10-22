package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.professoros.model.*

@Composable internal fun CatalogScreen(page:String,c:WorkspaceController,selectOffering:(Long)->Unit) {
    var search by remember(page){mutableStateOf("")}
    var selected by remember(page){mutableStateOf<Long?>(null)}
    val query=search.trim()
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Input("Search $page",search,{search=it},tag="record-search")
        when(page) {
            "Courses" -> {
                val row=c.catalog.courses.firstOrNull {it.id()==selected}
                Toolbar {
                    Action("New course","book",enabled=!c.busy,modifier=Modifier.testTag("new-course")){c.editCourse()}
                    Action("Edit",enabled=row!=null&&!c.busy){c.editCourse(row)}
                    Action("Details",enabled=row!=null&&!c.busy){row?.let {course->c.detail=Detail(course.courseCode()+" — "+course.courseName(),
                        "Department: ${course.department()}\nCredits: ${course.creditHours()}\n${course.description()}\n\nOfferings\n"+
                            c.catalog.offerings.filter {it.offering().courseId()==course.id()}.joinToString("\n"){it.label()}+"\n\nUpdated: ${course.updatedAt()}")}}
                    Action("Create offering",enabled=row!=null&&!c.busy){row?.let {c.editOffering(courseId=it.id())}}
                    Action("Delete",enabled=row!=null&&!c.busy){row?.let {c.confirm("Delete ${it.courseCode()}?","This cannot be undone. Courses with academic history cannot be deleted."){c.mutate {c.services.courses.delete(it.id())}}}}
                }
                RecordTable(c.catalog.courses.filter {listOf(it.courseCode(),it.courseName(),it.department()).any {s->s.contains(query,true)}},listOf(
                    TableColumn("Code",1f,Course::courseCode),TableColumn("Course",2f,Course::courseName),TableColumn("Department",1.3f,Course::department),TableColumn("Credits",.6f){it.creditHours().toString()}),Course::id,selected,{selected=it.id()},{c.editCourse(it)},"courses-table")
            }
            "Students" -> {
                val row=c.catalog.students.firstOrNull {it.id()==selected}
                Toolbar {
                    Action("New student","people",enabled=!c.busy,modifier=Modifier.testTag("new-student")){c.editStudent()}
                    Action("Edit",enabled=row!=null&&!c.busy){c.editStudent(row)}
                    Action("Details / enrollments",enabled=row!=null&&!c.busy){row?.let {student->c.inspect(student.toString()) {
                        "Email: ${student.email()}\n${student.notes()}\n\nEnrollment history\n"+c.services.academic.enrollments().findOfferingsForStudent(student.id()).joinToString("\n"){it.offering().label()+" · "+it.enrollment().status()}+"\n\nUpdated: ${student.updatedAt()}"
                    }}}
                    Action("Delete",enabled=row!=null&&!c.busy){row?.let {c.confirm("Delete ${it.fullName()}?","This cannot be undone. Enrollment history is protected."){c.mutate {c.services.academic.students().delete(it.id())}}}}
                }
                RecordTable(c.catalog.students.filter {it.matches(query)},listOf(TableColumn("Student ID",1f,Student::studentNumber),TableColumn("Name",1.5f,Student::fullName),TableColumn("Email",1.5f,Student::email),TableColumn("Updated",1f){it.updatedAt().toString().take(10)}),Student::id,selected,{selected=it.id()},{c.editStudent(it)},"students-table")
            }
            "Terms" -> {
                val row=c.catalog.terms.firstOrNull {it.id()==selected}
                Toolbar {
                    Action("New term","clock",enabled=!c.busy,modifier=Modifier.testTag("new-term")){c.editTerm()}
                    Action("Edit",enabled=row!=null&&!c.busy){c.editTerm(row)}
                    Action("Details",enabled=row!=null){row?.let {c.detail=Detail(it.label(),"${it.startDate()} — ${it.endDate()}\n${it.status()}\nUpdated: ${it.updatedAt()}")}}
                    Action("Delete",enabled=row!=null&&!c.busy){row?.let {c.confirm("Delete ${it.label()}?","Terms with offerings cannot be deleted."){c.mutate {c.services.academic.terms().delete(it.id())}}}}
                }
                RecordTable(c.catalog.terms.filter {it.label().contains(query,true)},listOf(TableColumn("Term",2f,AcademicTerm::label),TableColumn("Start"){it.startDate().toString()},TableColumn("End"){it.endDate().toString()},TableColumn("Status"){it.status().toString()}),AcademicTerm::id,selected,{selected=it.id()},{c.editTerm(it)},"terms-table")
            }
            "Offerings" -> {
                val row=c.catalog.offerings.firstOrNull {it.offering().id()==selected}
                Toolbar {
                    Action("New offering","book",enabled=!c.busy,modifier=Modifier.testTag("new-offering")){c.editOffering()}
                    Action("Edit",enabled=row!=null&&!c.busy){row?.let {c.editOffering(it.offering())}}
                    Action("Open / roster",enabled=row!=null&&!c.busy){row?.let {selectOffering(it.offering().id())}}
                    Action("Delete",enabled=row!=null&&!c.busy){row?.let {c.confirm("Delete offering?",it.label()+"\nExisting enrollment, lecture and exam history is protected."){c.mutate {c.services.academic.offerings().delete(it.offering().id())}}}}
                }
                RecordTable(c.catalog.offerings.filter {(it.label()+it.courseName()).contains(query,true)},listOf(TableColumn("Course",1.5f){it:OfferingSummary->it.courseCode()+" — "+it.courseName()},TableColumn("Term",1.5f){it.termName()+" "+it.academicYear()},TableColumn("Section"){it.offering().section()},TableColumn("Status"){it.offering().status().toString()}),{it.offering().id()},selected,{selected=it.offering().id()},{selectOffering(it.offering().id())},"offerings-table")
            }
        }
        Copy("${if(c.busy)"Working…" else "Saved locally"} · Select a row for actions; Enter opens it.",12,Ink.muted)
        c.error?.let {Copy(it,13,Ink.violet)}
    }
}

@Composable internal fun RosterScreen(c:WorkspaceController) {
    var selected by remember(c.offeringId){mutableStateOf<Long?>(null)}
    var query by remember {mutableStateOf("")}
    val active=c.active
    if(active==null) {Copy("Choose a teaching context in the top bar to manage its enrollment.",14,Ink.muted);return}
    val row=c.roster.firstOrNull {it.enrollment().id()==selected}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Copy(active.label(),18);Copy(active.offering().notes(),13,Ink.muted)
        Input("Search enrolled students",query,{query=it},tag="roster-search")
        Toolbar {
            Action("Enroll student","people",enabled=!c.busy,modifier=Modifier.testTag("enroll-student")){c.enrollStudent()}
            EnrollmentStatus.entries.forEach {status->Action(status.name.replace('_',' '),enabled=row!=null&&!c.busy) {
                row?.let {c.confirm("Set enrollment to $status?",it.student().toString()+"\nHistory will be preserved."){c.mutate {c.services.academic.enrollments().updateStatus(it.enrollment().id(),status)}}}
            }}
        }
        RecordTable(c.roster.filter {it.student().matches(query)},listOf(TableColumn("Student ID"){it:EnrolledStudent->it.student().studentNumber()},TableColumn("Name",1.5f){it.student().fullName()},TableColumn("Email",1.5f){it.student().email()},TableColumn("Status"){it.enrollment().status().toString()}),{it.enrollment().id()},selected,{selected=it.enrollment().id()},{c.detail=Detail(it.student().toString(),"${it.student().email()}\n${it.student().notes()}\n${it.enrollment().status()}")},"roster-table","No enrolled students. Create students in Students, then enroll them here.")
        Copy("Withdrawn and completed enrollments remain visible as academic history.",12,Ink.muted)
        c.error?.let {Copy(it,13,Ink.violet)}
    }
}
