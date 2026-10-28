package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.professoros.model.*
import com.professoros.service.ExamGenerator

internal fun WorkspaceController.openExam(id:Long?=null) {
    val scope=offeringId ?: return
    task({
        val service=services.academic.exams();val exam=id?.let {service.findById(scope,it)}
        if(exam!=null && exam.status()!=ExamStatus.DRAFT)throw DomainValidationException("This exam is no longer a draft. Refresh to see its status.")
        ExamDraft(scope,exam,service.bankForExam(scope,id))
    }) {examDraft=it}
}
internal fun WorkspaceController.saveExam() {
    if(busy)return
    val draft=examDraft?:return;val input=draft.input();val ids=draft.selected.map {it.questionId()}
    val marks=try {draft.marksForSave()}catch(f:DomainValidationException){error=f.message;return}
    mutate {services.academic.exams().save(draft.offeringId,draft.existing?.id(),input,ids,marks)}
}

@Composable internal fun ExamScreen(c:WorkspaceController) {
    var query by remember {mutableStateOf("")};var selected by remember(c.offeringId){mutableStateOf<Long?>(null)}
    val scope=c.offeringId
    if(scope==null){Copy("Choose a teaching context to build exams from its course's question bank.",14,Ink.muted);return}
    val row=c.exams.firstOrNull {it.id()==selected};val draft=row?.status()==ExamStatus.DRAFT
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Copy(c.active?.label().orEmpty(),14,Ink.violet)
        Input("Search exams",query,{query=it},tag="exam-search")
        Toolbar {
            Action("New exam","book",variant=ActionVariant.PRIMARY,enabled=!c.busy,modifier=Modifier.testTag("new-exam")){c.openExam()}
            Action("Build / edit",variant=ActionVariant.PRIMARY,enabled=draft&&!c.busy){row?.let {c.openExam(it.id())}}
            Action("Preview / export PDF",enabled=row!=null&&!c.busy,modifier=Modifier.testTag("open-pdf")){row?.let {c.openPdf(it.id())}}
            Action("Details",enabled=row!=null&&!c.busy){row?.let {exam->c.inspect("Exam details · saved snapshots") {
                val saved=c.services.academic.exams().findById(scope,exam.id())
                "${saved.title()} · ${saved.status()}\n${saved.date()} · ${saved.durationMinutes()} minutes\nCalculated ${saved.calculatedMarks()} / intended ${saved.intendedMarks()} marks${if(saved.marksDiffer())" · Warning: totals differ" else ""}\n\n${saved.instructions()}\n\n"+
                    saved.questions().joinToString("\n\n──────────\n\n"){"${it.position()}. "+questionDetails(it)}
            }}}
            Action("Finalize",variant=ActionVariant.WARNING,enabled=draft&&!c.busy){row?.let {c.confirm("Finalize exam?","${it.title()}\nCalculated ${it.calculatedMarks()} / intended ${it.intendedMarks()} marks${if(it.marksDiffer())" — Warning: totals differ." else "."}\nThis locks content and records usage once."){c.mutate {c.services.academic.exams().finalizeExam(scope,it.id())}}}}
            Action("Archive",variant=ActionVariant.WARNING,enabled=row?.status()==ExamStatus.FINALIZED&&!c.busy){row?.let {c.confirm("Archive exam?",it.title()+"\nIts questions and history will be preserved."){c.mutate {c.services.academic.exams().archive(scope,it.id())}}}}
            Action("Delete draft",variant=ActionVariant.DESTRUCTIVE,enabled=draft&&!c.busy){row?.let {c.confirm("Delete draft exam?",it.title()+"\nThis permanently deletes the draft and its saved selection."){c.mutate {c.services.academic.exams().delete(scope,it.id())}}}}
        }
        RecordTable(c.exams.filter {it.title().contains(query,true)},listOf(TableColumn("Exam",2f,Exam::title),TableColumn("Date"){it.date().toString()},TableColumn("Status"){it.status().name},TableColumn("Marks",1.5f){"${it.calculatedMarks()} / ${it.intendedMarks()}${if(it.marksDiffer())" !" else ""}"},TableColumn("Questions",.7f){it.questions().size.toString()}),Exam::id,selected,{selected=it.id()},{if(it.status()==ExamStatus.DRAFT)c.openExam(it.id())},"exams-table","No exams yet. Create a draft and choose questions from this course's bank.")
        Copy("PDFs use saved exam snapshots. Student papers and answer keys are separate documents.",12,Ink.muted)
        c.error?.let {Copy(it,13,Ink.violet)}
    }
}

@Composable internal fun ExamEditor(c:WorkspaceController,d:ExamDraft) {
    var tab by remember(d){mutableStateOf("Question bank")}
    var query by remember {mutableStateOf("")};var type by remember {mutableStateOf("")}
    var difficulty by remember {mutableStateOf("")};var topic by remember {mutableStateOf("")}
    var bankSelection by remember {mutableStateOf<Long?>(null)}
    val bankSelections=remember {mutableStateListOf<Long>()}
    Modal(if(d.existing==null)"New exam" else "Edit draft · ${d.existing.title()}",c::closeEditor,wide=true,submit=c::saveExam,footer={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Copy("${d.selected.size} questions · Calculated ${d.total} / intended ${d.intended} marks${if(d.mismatch)" · Warning: totals differ" else ""}",14,if(d.mismatch)Ink.violet else Ink.text,modifier=Modifier.testTag("exam-totals"))
            c.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("form-error"))}
            Toolbar {
                Action("Save exam","check",variant=ActionVariant.SUCCESS,enabled=!c.busy,modifier=Modifier.testTag("save-exam")) {
                    c.saveExam()
                }
                Action("Cancel",enabled=!c.busy,onClick=c::closeEditor)
            }
        }
    }) {
        CompositionLocalProvider(LocalControlsEnabled provides !c.busy) {
            Copy(c.catalog.offerings.firstOrNull {it.offering().id()==d.offeringId}?.label().orEmpty(),13,Ink.violet)
            Input("Exam title *",d.title,{d.title=it},tag="exam-title")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Input("Date * (yyyy-MM-dd)",d.date,{d.date=it},Modifier.weight(1.5f),tag="exam-date")
                Input("Duration (minutes) *",d.duration,{d.duration=it},Modifier.weight(1f),tag="exam-duration")
                Input("Intended marks *",d.intended,{d.intended=it},Modifier.weight(1f),tag="exam-intended")
            }
            Toolbar {listOf("Question bank","Composition","Instructions","Generate").forEach {label->
                Action(label,active=tab==label,modifier=Modifier.testTag("exam-tab-$label")){tab=label;c.error=null}
            }}
            when(tab) {
                "Question bank" -> {
                    Input("Search questions",query,{query=it},tag="exam-bank-search")
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)){Select("Type",type,listOf(Choice("","All types"))+options(QuestionType.entries),{type=it},"exam-type")}
                        Column(Modifier.weight(1f)){Select("Difficulty",difficulty,listOf(Choice("","All levels"))+options(Difficulty.entries),{difficulty=it},"exam-difficulty")}
                        Input("Topic contains",topic,{topic=it},Modifier.weight(1f),tag="exam-topic")
                    }
                    val bank=d.bank.filter {it.matches(query)&&(type.isEmpty()||it.type().name==type)&&(difficulty.isEmpty()||it.difficulty().name==difficulty)&&it.topic().contains(topic,true)}
                    RecordTable(bank,listOf(TableColumn("Question",3f,Question::text),TableColumn("Type"){it.type().name.replace('_',' ')},TableColumn("Difficulty"){it.difficulty().name},TableColumn("Marks",.6f){it.marks().toPlainString()}),Question::id,bankSelection,{
                        bankSelection=it.id();if(!bankSelections.remove(it.id()))bankSelections.add(it.id())
                    },{d.add(it)},"exam-bank-table","No matching questions for this course. Add questions in Question Bank, or clear filters.",selection=bankSelections.toSet())
                    Toolbar {
                        Action("Add selected (${bankSelections.size})","check",variant=ActionVariant.PRIMARY,enabled=bankSelections.isNotEmpty(),modifier=Modifier.testTag("add-exam-question")){bankSelections.forEach {id->d.bank.firstOrNull {it.id()==id}?.let {d.add(it)}};bankSelections.clear()}
                        Action("Inspect question",enabled=bankSelection!=null){d.bank.firstOrNull {it.id()==bankSelection}?.let {c.detail=Detail("Question",questionDetails(ExamQuestion.snapshot(it,1)))}}
                        Action("Clear filters"){query="";type="";difficulty="";topic=""}
                    }
                    Copy("Click rows to toggle selection; Add selected adds them together. Enter adds a focused row. Only this offering's course is eligible; saved snapshots are preserved.",12,Ink.muted)
                }
                "Composition" -> {
                    Copy("Question order below is the saved exam order. Select a row to move or remove it.",13,Ink.muted)
                    // Composition is intentionally not sortable: ordering is an explicit edit.
                    d.selected.forEachIndexed {index,q->
                        Panel(Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Copy("${index+1}. ${q.text()}",14,modifier=Modifier.weight(1f).testTag("composition-$index"),lines=3)
                            Input("Marks for this exam",d.marks[q.questionId()]?:q.marks().toPlainString(),{d.marks[q.questionId()]=it},Modifier.width(180.dp),tag="exam-marks-${q.questionId()}")
                            }
                            Copy("${q.type()} · Question bank default unchanged",12,Ink.muted)
                            Toolbar {
                                Action("Up",enabled=index>0,modifier=Modifier.testTag("move-up-$index")){d.move(index,-1)}
                                Action("Down",enabled=index<d.selected.lastIndex){d.move(index,1)}
                                Action("Remove",variant=ActionVariant.DESTRUCTIVE,modifier=Modifier.testTag("remove-question-$index")){d.selected.removeAt(index)}
                                Action("Details"){c.detail=Detail("Saved question content",questionDetails(q))}
                            }
                        }
                    }
                    if(d.selected.isEmpty())Copy("Your exam starts here. Add questions from the bank or generate a constrained selection.",14,Ink.muted)
                }
                "Instructions" -> Input("Student instructions",d.instructions,{d.instructions=it},multiline=true,tag="exam-instructions")
                "Generate" -> GenerationPanel(c,d) {tab="Composition"}
            }
        }
    }
}

@Composable private fun GenerationPanel(c:WorkspaceController,d:ExamDraft,done:()->Unit) {
    var count by remember {mutableStateOf("10")}
    val difficulties=remember {mutableStateMapOf<String,String>()};val types=remember {mutableStateMapOf<String,String>()};val topics=remember {mutableStateMapOf<String,String>()}
    var group by remember {mutableStateOf("Difficulty")}
    Input("Question count *",count,{count=it},tag="generation-count")
    Copy("Blank distribution = unrestricted. Entered counts must sum to the question count; omitted categories then mean zero. Marks are not constrained.",13,Ink.muted)
    Toolbar {listOf("Difficulty","Type","Topic").forEach {Action(it,active=it==group){group=it}}}
    val keys=when(group){"Difficulty"->Difficulty.entries.map {it.name};"Type"->QuestionType.entries.map {it.name};else->d.bank.map {ExamGenerator.topicKey(it.topic())}.distinct().sorted()}
    val values=when(group){"Difficulty"->difficulties;"Type"->types;else->topics}
    keys.forEach {key->Input(key.ifEmpty {"(Untagged)"},values[key].orEmpty(),{values[key]=it},tag="quota-$key")}
    Action("Generate selection","check",variant=ActionVariant.PRIMARY,enabled=!c.busy,modifier=Modifier.testTag("generate-selection")) {
        val request=GenerationRequest(count.toIntOrNull(),difficulties.filterValues {it.isNotBlank()}.mapKeys {Difficulty.valueOf(it.key)}.mapValues {it.value.toIntOrNull()},
            types.filterValues {it.isNotBlank()}.mapKeys {QuestionType.valueOf(it.key)}.mapValues {it.value.toIntOrNull()},topics.filterValues {it.isNotBlank()}.mapValues {it.value.toIntOrNull()})
        val generate={c.task({c.services.academic.exams().generate(d.offeringId,d.existing?.id(),request)}) {questions->d.replace(questions);done()}}
        if(d.selected.isNotEmpty())c.confirm("Replace selected questions?","Generation replaces the local selection only after it succeeds. Nothing is persisted until Save.",generate) else generate()
    }
}
