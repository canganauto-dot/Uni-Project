package com.professoros.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.professoros.model.*
import com.professoros.service.AttendanceCalculator
import kotlinx.coroutines.launch

@Composable internal fun AttendanceScreen(c:WorkspaceController) {
    val editor=c.attendance
    when {
        c.offeringId==null -> Copy("Choose a teaching context to record attendance.",14,Ink.muted)
        editor==null -> Column {
            Copy(if(c.busy)"Loading saved attendance…" else "Attendance is not loaded. Use Refresh to retry.",14,Ink.muted)
            c.error?.let {Copy(it,13,Ink.violet)}
        }
        else -> AttendanceContent(c,editor)
    }
}

@Composable private fun AttendanceContent(c:WorkspaceController,editor:AttendanceEditor) {
    var query by remember(editor.book.offeringId()) {mutableStateOf("")}
    var selected by remember(editor.book.offeringId()) {mutableStateOf<Long?>(null)}
    var bulk by remember {mutableStateOf("PRESENT")}
    var sort by remember {mutableStateOf("number")}
    val book=editor.book
    val row=book.students().find {it.enrollment().id()==selected}
    Column(Modifier.fillMaxWidth().onPreviewKeyEvent {
        if(it.type==KeyEventType.KeyDown && (it.isCtrlPressed||it.isMetaPressed) && it.key==Key.S){c.saveAttendance();true}else false
    },verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Copy(c.active?.label().orEmpty(),14,Ink.violet,modifier=Modifier.weight(1f))
            Copy(if(editor.dirty)"● Unsaved changes" else "Saved locally",12,if(editor.dirty)Ink.violet else Ink.muted,modifier=Modifier.testTag("attendance-save-state"))
        }
        if(book.lectures().isEmpty()) Panel(Modifier.fillMaxWidth()) {
            Copy("No attendance sessions yet",18,weight=FontWeight.SemiBold)
            Copy("Create a lecture in Lectures, then return here to mark its enrolled students.",14,Ink.muted)
        } else {
            Select("Lecture / date",editor.lectureId?.toString().orEmpty(),book.lectures().map {
                Choice(it.id().toString(),"${it.plannedDate()} · ${it.lectureNumber()}. ${it.title()}"+if(it.status()==LectureStatus.CANCELLED)" · Cancelled" else "")
            },{c.chooseAttendanceLecture(it.toLong())},"attendance-lecture")
            Toolbar {
                Action("Save attendance","check",enabled=editor.dirty&&!editor.readOnly&&!c.busy,modifier=Modifier.testTag("save-attendance"),onClick=c::saveAttendance)
                Action("Export to Excel",enabled=!c.busy,modifier=Modifier.testTag("export-attendance")){c.exportGradeWorkbook()}
                Action("Discard / reload",enabled=!c.busy,modifier=Modifier.testTag("reload-attendance")){c.guard {c.refresh()}}
                Action("Student history","calendar",enabled=row!=null&&!c.busy,modifier=Modifier.testTag("attendance-history")){row?.let {c.detail=Detail("Attendance history · ${it.student().fullName()}",editor.history(it))}}
            }
            if(editor.readOnly)Copy("Cancelled lecture · History is read-only and excluded from summaries.",13,Ink.violet,modifier=Modifier.testTag("attendance-readonly"))
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Input("Search student number or name",query,{query=it},Modifier.weight(2f),tag="attendance-search")
                Column(Modifier.weight(1f)){Select("Sort roster",sort,listOf(Choice("number","Student number"),Choice("name","Student name")),{sort=it})}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalControlsEnabled provides (!c.busy&&!editor.readOnly)) {
                        Select("Bulk status · entire roster, including hidden rows",bulk,attendanceChoices.map {Choice(it?.name?:"UNMARKED",attendanceLabel(it))},{bulk=it},"attendance-bulk-status")
                    }
                }
                Action("Mark roster","people",enabled=!editor.readOnly&&!c.busy&&book.students().isNotEmpty(),modifier=Modifier.testTag("bulk-attendance")) {
                    c.markAttendanceRoster(if(bulk=="UNMARKED")null else AttendanceStatus.valueOf(bulk))
                }
            }
            Copy("P Present · A Absent · L Late · E Excused · U Unmarked · ↑ ↓ move students · Ctrl+S saves",12,Ink.muted)
            val rows=book.students().filter {("${it.student().studentNumber()} ${it.student().fullName()}").contains(query.trim(),true)}
                .sortedBy {if(sort=="name")it.student().fullName().lowercase() else it.student().studentNumber()}
            AttendanceRoster(editor,rows,selected,{selected=it},!c.busy)
        }
        Panel(Modifier.fillMaxWidth()) {
            Copy("Saved course summary",14,weight=FontWeight.SemiBold)
            Copy(AttendanceCalculator.course(book).display(),13,Ink.violet,modifier=Modifier.testTag("attendance-summary"))
            Copy("Withdrawn enrollments excluded. Save changes to update percentages and history.",12,Ink.muted)
        }
        Copy(AttendanceCalculator.POLICY,12,Ink.muted)
        c.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("attendance-error"))}
    }
}

@Composable private fun AttendanceRoster(editor:AttendanceEditor,rows:List<EnrolledStudent>,selected:Long?,select:(Long)->Unit,enabled:Boolean) {
    val vertical=rememberLazyListState();val horizontal=rememberScrollState();val scope=rememberCoroutineScope()
    val focus=remember(rows.map {it.enrollment().id()}) {rows.associate {it.enrollment().id() to FocusRequester()}}
    val saved=remember(editor.book) {editor.book.students().associate {it.enrollment().id() to AttendanceCalculator.student(editor.book,it.enrollment().id())}}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=maxWidth.coerceAtLeast(720.dp)
        Column(Modifier.fillMaxWidth().border(1.dp,Ink.border,RoundedCornerShape(10.dp)).testTag("attendance-roster")) {
            Column(Modifier.horizontalScroll(horizontal)) {
                Row(Modifier.width(width).background(Ink.raised).padding(12.dp)) {
                    Copy("Student / enrollment",12,Ink.muted,modifier=Modifier.weight(1f))
                    Copy("Session attendance",12,Ink.muted,modifier=Modifier.width(390.dp))
                    Copy("Saved %",12,Ink.muted,modifier=Modifier.width(90.dp))
                }
                if(rows.isEmpty())Copy("No enrolled students match. Enroll students through Teaching context or clear the search.",14,Ink.muted,modifier=Modifier.width(width).padding(24.dp))
                else LazyColumn(Modifier.width(width).heightIn(max=350.dp),state=vertical) {
                    itemsIndexed(rows,key={_,r->r.enrollment().id()}) {index,row->
                        val id=row.enrollment().id();var focused by remember {mutableStateOf(false)}
                        val value=editor.value(id)
                        Row(Modifier.fillMaxWidth().heightIn(min=86.dp).background(if(selected==id)Ink.selected else if(index%2==0)Ink.panel else Ink.background)
                            .focusRequester(focus.getValue(id)).onFocusChanged {focused=it.hasFocus;if(it.hasFocus)select(id)}
                            .border(1.dp,if(focused)Ink.violet else Ink.border.copy(alpha=.3f))
                            .onPreviewKeyEvent {event->
                                if(!enabled||event.type!=KeyEventType.KeyDown||event.isCtrlPressed||event.isMetaPressed||event.isAltPressed)false
                                else if(event.key==Key.DirectionUp||event.key==Key.DirectionDown) {
                                    val next=index+if(event.key==Key.DirectionUp)-1 else 1
                                    if(next in rows.indices){scope.launch {vertical.scrollToItem(next);focus.getValue(rows[next].enrollment().id()).requestFocus()};true}else false
                                } else if(!editor.readOnly && event.key in listOf(Key.P,Key.A,Key.L,Key.E,Key.U,Key.Delete,Key.Backspace)) {
                                    editor.set(id,when(event.key){Key.P->AttendanceStatus.PRESENT;Key.A->AttendanceStatus.ABSENT;Key.L->AttendanceStatus.LATE;Key.E->AttendanceStatus.EXCUSED;else->null});true
                                } else false
                            }.clickable(enabled=enabled){select(id)}.testTag("attendance-row-$id")
                            .semantics {this.selected=selected==id;contentDescription="${row.student().studentNumber()} ${row.student().fullName()} attendance: ${attendanceLabel(value)}"}
                            .padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end=10.dp)) {
                                Copy(row.student().fullName(),14,lines=2)
                                Copy("${row.student().studentNumber()} · ${row.enrollment().status().name.lowercase()}",11,Ink.muted)
                            }
                            Row(Modifier.width(390.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                attendanceChoices.forEach {status->
                                    Box(Modifier.width(73.dp).heightIn(min=36.dp).background(if(value==status)Ink.accentSurface else Ink.background,RoundedCornerShape(6.dp))
                                        .border(1.dp,if(value==status)Ink.violet else Ink.border,RoundedCornerShape(6.dp))
                                        .selectable(selected=value==status,enabled=enabled&&!editor.readOnly,role=Role.RadioButton){select(id);editor.set(id,status)}
                                        .testTag("attendance-$id-${status?.name?:"UNMARKED"}").semantics {contentDescription="${row.student().fullName()}: ${attendanceLabel(status)}"}
                                        .padding(horizontal=4.dp,vertical=9.dp),contentAlignment=Alignment.Center) {
                                        Copy(attendanceLabel(status),11,if(value==status)Ink.violet else Ink.muted,lines=1)
                                    }
                                }
                            }
                            Copy(saved[id]?.percentage()?.let {"$it%"}?:"Not rated",12,Ink.muted,modifier=Modifier.width(90.dp).testTag("attendance-percent-$id"))
                        }
                    }
                }
            }
            HorizontalScrollbar(rememberScrollbarAdapter(horizontal),Modifier.fillMaxWidth().height(10.dp))
        }
    }
}
