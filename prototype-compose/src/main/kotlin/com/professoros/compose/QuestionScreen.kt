package com.professoros.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.professoros.model.*
import java.awt.FileDialog
import java.awt.Frame
import java.awt.EventQueue
import java.nio.file.Path

class QuestionDraft(val existing:Question?,courses:List<Choice>,courseId:Long?) {
    val form=RecordForm(if(existing==null)"New question" else "Edit question",listOf(
        Field("course","Catalog course *",(existing?.courseId()?:courseId)?.toString().orEmpty(),choices=courses),
        Field("type","Question type *",existing?.type()?.name?:"MCQ",choices=options(QuestionType.entries)),
        Field("difficulty","Difficulty *",existing?.difficulty()?.name?:"MEDIUM",choices=options(Difficulty.entries)),
        Field("marks","Marks *",existing?.marks()?.toPlainString()?:"1"),Field("topic","Topic",existing?.topic().orEmpty()),
        Field("text","Question text *",existing?.text().orEmpty(),true),Field("answer","Answer / model answer",existing?.answer().orEmpty(),true),
        Field("explanation","Explanation",existing?.explanation().orEmpty(),true),Field("image","Optional absolute image path",existing?.imagePath().orEmpty()))) {}
    val choices=mutableStateListOf<AnswerChoice>().apply {addAll(existing?.choices()?.takeIf {it.isNotEmpty()}?:listOf(AnswerChoice("",false),AnswerChoice("",false)))}
    private val original=choices.toList()
    val dirty get()=form.dirty || choices.toList()!=original
    fun input():QuestionInput {
        val type=QuestionType.valueOf(form["type"])
        return QuestionInput(form["course"].toLongOrNull(),type,form["text"],form["topic"],Difficulty.valueOf(form["difficulty"]),
            form["marks"].trim().toBigDecimalOrNull(),form["answer"],form["explanation"],form["image"],if(type==QuestionType.MCQ)choices.toList() else emptyList())
    }
}
internal fun WorkspaceController.editQuestion(existing:Question?=null,courseId:Long?=null) {questionDraft=QuestionDraft(existing,catalog.courseChoices(),courseId)}
internal fun WorkspaceController.saveQuestion() {
    if(busy)return
    val draft=questionDraft?:return;val input=draft.input()
    mutate {if(draft.existing==null)services.academic.questions().create(input) else services.academic.questions().update(draft.existing.id(),input)}
}
internal fun questionDetails(q:ExamQuestion):String = "${q.type()} · ${q.difficulty()} · ${q.marks()} marks\nTopic: ${q.topic()}\n\n${q.text()}\n\n"+
    q.choices().mapIndexed {index,choice->"${index+1}. ${choice.text()}${if(choice.correct())" ✓ Correct" else ""}"}.joinToString("\n")+
    "\n\nAnswer: ${q.answer()}\n\nExplanation: ${q.explanation()}\nImage: ${q.imagePath()}"

@Composable internal fun QuestionEditor(c:WorkspaceController,draft:QuestionDraft) {
    val f=draft.form
    Modal(f.title,c::closeEditor,submit=c::saveQuestion,footer={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        c.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("form-error"))}
        Toolbar {
            Action("Save question","check",enabled=!c.busy,modifier=Modifier.testTag("save-question")) {
                c.saveQuestion()
            }
            Action("Cancel",enabled=!c.busy,onClick=c::closeEditor)
        }
    }}) {
        CompositionLocalProvider(LocalControlsEnabled provides !c.busy) {
            f.fields.filter {it.key!="answer"}.forEach {field->
                if(field.choices!=null)Select(field.label,f[field.key],field.choices,{f.values[field.key]=it},"question-"+field.key)
                else Input(field.label,f[field.key],{f.values[field.key]=it},multiline=field.multiline,tag="question-"+field.key)
            }
            Action("Browse for image…",enabled=!c.busy) {
                c.task({
                    var result:String?=null
                    EventQueue.invokeAndWait {
                        val chooser=FileDialog(null as Frame?,"Select a question image",FileDialog.LOAD)
                        try {chooser.isVisible=true;chooser.file?.let {result=Path.of(chooser.directory,it).toString()}} finally {chooser.dispose()}
                    };result
                }) {it?.let {f.values["image"]=it}}
            }
            Copy("Images are referenced, not copied. Keep the file at its saved location.",12,Ink.muted)
            when(f["type"]) {
                "MCQ" -> {
                    Copy("Answer choices · select exactly one correct answer",14)
                    draft.choices.forEachIndexed {index,choice->
                        Input("Choice ${index+1}",choice.text(),{draft.choices[index]=AnswerChoice(it,choice.correct())},multiline=true,tag="choice-$index")
                        Toolbar {
                            Action(if(choice.correct())"Correct answer" else "Mark correct",if(choice.correct())"check" else "arrow",modifier=Modifier.testTag("correct-$index")) {
                                draft.choices.indices.forEach {i->draft.choices[i]=AnswerChoice(draft.choices[i].text(),i==index)}
                            }
                            Action("Remove choice",enabled=draft.choices.size>2){draft.choices.removeAt(index)}
                        }
                    }
                    Action("Add choice",enabled=draft.choices.size<10,modifier=Modifier.testTag("add-choice")){draft.choices.add(AnswerChoice("",false))}
                }
                "TRUE_FALSE" -> Select("Correct answer *",f["answer"],listOf(Choice("TRUE","True"),Choice("FALSE","False")),{f.values["answer"]=it},"question-answer")
                else -> Input("Answer / model answer",f["answer"],{f.values["answer"]=it},multiline=true,tag="question-answer")
            }
        }
    }
}

@Composable internal fun QuestionScreen(c:WorkspaceController) {
    var query by remember {mutableStateOf("")};var course by remember {mutableStateOf("")}
    var type by remember {mutableStateOf("")};var difficulty by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf<Long?>(null)}
    val row=c.questions.firstOrNull {it.id()==selected}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Input("Search question text, topic or answer",query,{query=it},tag="question-search")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(2f)){Select("Course",course,listOf(Choice("","All courses"))+c.catalog.courseChoices(),{course=it},"filter-course")}
            Column(Modifier.weight(1f)){Select("Type",type,listOf(Choice("","All types"))+options(QuestionType.entries),{type=it},"filter-type")}
            Column(Modifier.weight(1f)){Select("Difficulty",difficulty,listOf(Choice("","All levels"))+options(Difficulty.entries),{difficulty=it},"filter-difficulty")}
        }
        Toolbar {
            Action("New question","book",enabled=c.catalog.courses.isNotEmpty()&&!c.busy,modifier=Modifier.testTag("new-question")){c.editQuestion(courseId=course.toLongOrNull())}
            Action("Edit",enabled=row!=null&&!c.busy){row?.let {c.editQuestion(it)}}
            Action("Details",enabled=row!=null){row?.let {c.detail=Detail("Question details",questionDetails(ExamQuestion.snapshot(it,1))+"\n\nUsage: ${it.usageCount()} · Last used: ${it.lastUsedDate()?:"Never"}\nCreated: ${it.createdAt()}\nUpdated: ${it.updatedAt()}")}}
            Action("Delete",enabled=row!=null&&!c.busy){row?.let {c.confirm("Delete question?",it.text()+"\nDeletion may be restricted by saved exam history."){c.mutate {c.services.academic.questions().delete(it.id())}}}}
            Action("Clear filters"){query="";course="";type="";difficulty=""}
        }
        val rows=c.questions.filter {it.matches(query)&&(course.isEmpty()||it.courseId().toString()==course)&&(type.isEmpty()||it.type().name==type)&&(difficulty.isEmpty()||it.difficulty().name==difficulty)}
        RecordTable(rows,listOf(TableColumn("Course",.8f){q:Question->c.catalog.courses.firstOrNull {it.id()==q.courseId()}?.courseCode().orEmpty()},TableColumn("Question",2.5f,Question::text),TableColumn("Topic",1f,Question::topic),TableColumn("Type",1.2f){it.type().name.replace('_',' ')},TableColumn("Difficulty",.8f){it.difficulty().name},TableColumn("Marks",.6f){it.marks().toPlainString()}),Question::id,selected,{selected=it.id()},{c.editQuestion(it)},"questions-table",if(c.catalog.courses.isEmpty())"Create a catalog course first." else "No matching questions. Create a question or clear your filters.")
        Copy("${c.questions.size} saved questions · Usage is recorded when an exam is finalized.",12,Ink.muted)
        c.error?.let {Copy(it,13,Ink.violet)}
    }
}
