package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.professoros.model.*

internal fun WorkspaceController.editLecture(existing:Lecture?=null) {
    val scope=offeringId ?: return
    form=RecordForm(if(existing==null)"New lecture" else "Edit lecture",listOf(
        Field("number","Lecture number *",existing?.lectureNumber()?.toString().orEmpty()),Field("title","Title *",existing?.title().orEmpty()),
        Field("date","Planned date * (yyyy-MM-dd)",existing?.plannedDate()?.toString().orEmpty()),
        Field("duration","Duration in minutes *",existing?.durationMinutes()?.toString()?:"60"),
        Field("status","Status",existing?.status()?.name?:"PLANNED",choices=options(LectureStatus.entries)),
        Field("topics","Topics",existing?.topics().orEmpty(),true),Field("description","Description",existing?.description().orEmpty(),true),
        Field("notes","Professor notes",existing?.professorNotes().orEmpty(),true))) { f->
        val input=LectureInput(f.number("number"),f["title"],f.date("date"),f["topics"],f["description"],f.number("duration"),LectureStatus.valueOf(f["status"]),f["notes"])
        if(existing==null)services.academic.lectures().create(scope,input) else services.academic.lectures().update(scope,existing.id(),input)
    }
}

@Composable internal fun LectureScreen(c:WorkspaceController) {
    var query by remember {mutableStateOf("")};var status by remember {mutableStateOf("")}
    var selected by remember(c.offeringId){mutableStateOf<Long?>(null)}
    val scope=c.offeringId
    if(scope==null){Copy("Choose a teaching context to organize its lectures.",14,Ink.muted);return}
    val overview=c.lectures;val row=overview?.lectures()?.firstOrNull {it.id()==selected}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Copy(c.active?.label().orEmpty(),14,Ink.violet)
        overview?.progress()?.let {p->Panel(Modifier.fillMaxWidth()) {
            Copy("${p.total()} lectures · ${p.completed()} completed · ${p.partial()} partial",16)
            Copy("${p.completionPercent()}% complete · ${p.scheduleStatus()}",14,Ink.violet)
            Copy("Cancelled lectures are excluded. Partially completed lectures do not count as completed.",12,Ink.muted)
        }}
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Input("Search lectures",query,{query=it},Modifier.weight(2f),tag="lecture-search")
            Column(Modifier.weight(1f)){Select("Status",status,listOf(Choice("","All statuses"))+options(LectureStatus.entries),{status=it})}
        }
        Toolbar {
            Action("New lecture","book",enabled=!c.busy,modifier=Modifier.testTag("new-lecture")){c.editLecture()}
            Action("Edit",enabled=row!=null&&!c.busy){row?.let {c.editLecture(it)}}
            Action("Details",enabled=row!=null){row?.let {c.detail=Detail("${it.lectureNumber()}. ${it.title()}","${it.plannedDate()} · ${it.durationMinutes()} minutes · ${it.status()}\n\nTopics\n${it.topics()}\n\nDescription\n${it.description()}\n\nProfessor notes\n${it.professorNotes()}\n\nUpdated: ${it.updatedAt()}")}}
            Action("Delete",enabled=row!=null&&!c.busy){row?.let {c.confirm("Delete lecture ${it.lectureNumber()}?",it.title()+"\nThis cannot be undone. Existing attendance history is protected."){c.mutate {c.services.academic.lectures().delete(scope,it.id())}}}}
        }
        RecordTable(overview?.lectures().orEmpty().filter {it.matches(query)&&(status.isEmpty()||it.status().name==status)},listOf(
            TableColumn("No.",.5f){it:Lecture->it.lectureNumber().toString()},TableColumn("Lecture",2f,Lecture::title),
            TableColumn("Planned date"){it.plannedDate().toString()},TableColumn("Minutes",.6f){it.durationMinutes().toString()},TableColumn("Status",1.3f){it.status().name.replace('_',' ')}),
            Lecture::id,selected,{selected=it.id()},{c.editLecture(it)},"lectures-table","No matching lectures. Add a lecture or clear your filters.")
        c.error?.let {Copy(it,13,Ink.violet)}
    }
}
