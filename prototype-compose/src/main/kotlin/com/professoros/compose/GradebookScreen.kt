package com.professoros.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.professoros.model.*
import com.professoros.service.*
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

@Composable internal fun GradebookScreen(c:WorkspaceController) {
    val editor=c.gradebook
    when {
        c.offeringId==null -> Copy("Choose a teaching context to manage its gradebook.",14,Ink.muted)
        editor==null -> Column {
            Copy(if(c.busy)"Loading saved grades…" else "Gradebook is not loaded. Use Refresh to retry.",14,Ink.muted)
            c.error?.let {Copy(it,13,Ink.violet)}
        }
        else -> GradebookContent(c,editor)
    }
}

@Composable private fun GradebookContent(c:WorkspaceController,editor:GradebookEditor) {
    val book=editor.book;val summary=editor.summary()
    var tab by remember {mutableStateOf("Grades")}
    var query by remember {mutableStateOf("")}
    var categoryId by remember(book.offeringId()) {mutableStateOf<Long?>(null)}
    var assessmentId by remember(book.offeringId()) {mutableStateOf<Long?>(null)}
    val category=book.categories().find {it.id()==categoryId};val assessment=book.assessments().find {it.id()==assessmentId}
    Column(Modifier.fillMaxWidth().onPreviewKeyEvent {
        if(it.type==KeyEventType.KeyDown && it.key==Key.S && (it.isCtrlPressed||it.isMetaPressed)){c.saveGrades();true}else false
    },verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Copy(c.active?.label().orEmpty(),14,Ink.violet)
            Copy(if(editor.dirty)"● Unsaved changes" else "Saved locally",12,if(editor.dirty)Ink.violet else Ink.muted,modifier=Modifier.testTag("grade-save-state"))
        }
        Copy(GradeCalculator.configurationMessage(book),13,Ink.violet,modifier=Modifier.testTag("grade-weights"))
        Toolbar {listOf("Grades","Assessments","Categories & scale").forEach {name->Action(name,if(tab==name)"check" else "arrow",modifier=Modifier.testTag("grade-tab-$name")){tab=name}}}
        when(tab) {
            "Grades" -> {
                Toolbar {
                    Action("Save grades","check",enabled=editor.dirty&&!c.busy,modifier=Modifier.testTag("save-grades"),onClick=c::saveGrades)
                    Action("Discard / reload",enabled=!c.busy,modifier=Modifier.testTag("discard-grades")){c.guard {c.refresh()}}
                    Action("Bulk entry",enabled=book.assessments().isNotEmpty()&&!c.busy,modifier=Modifier.testTag("bulk-grades")){c.error=null;c.gradeBulk=GradeBulk(book.assessments())}
                    Action("Export to Excel",enabled=!c.busy){c.exportGradeWorkbook()}
                }
                Input("Search student number or name",query,{query=it},tag="grade-search")
                Copy("Blank = ungraded · 0 = actual zero · Enter / ↑ ↓ move rows · Tab moves columns · Ctrl+S saves",12,Ink.muted)
                if(book.assessments().isEmpty()) Panel(Modifier.fillMaxWidth()) {
                    Copy("Set up your first assessment",18,weight=FontWeight.SemiBold)
                    Copy("Create weighted categories, then add assessments to begin recording scores.",14,Ink.muted)
                    Action("Manage categories"){tab="Categories & scale"}
                } else GradeGrid(editor,summary,query,!c.busy)
                Copy(summary.statistics,13,Ink.muted,modifier=Modifier.testTag("grade-statistics"))
                summary.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("grade-validation"))}
            }
            "Assessments" -> {
                Toolbar {
                    Action("New assessment","exam",modifier=Modifier.testTag("new-assessment")){c.editAssessment()}
                    Action("Edit",enabled=assessment!=null){assessment?.let {c.editAssessment(it)}}
                    Action("Delete",enabled=assessment!=null){if(assessment!=null&&c.gradeConfigurable())c.confirm("Delete assessment and its grades?","${assessment.title()}\nAll saved grades for this assessment will be permanently removed. This cannot be undone."){c.mutate {c.services.academic.grades().deleteAssessment(book.offeringId(),assessment.id())}}}
                }
                RecordTable(book.assessments(),listOf(TableColumn("Assessment",2f){a:Assessment->a.title()},TableColumn("Maximum"){it.maximumMarks().toPlainString()},TableColumn("Date"){it.date().toString()},TableColumn("Category"){a->book.categories().find {it.id()==a.categoryId()}?.name().orEmpty()}),
                    Assessment::id,assessmentId,{assessmentId=it.id()},{c.editAssessment(it)},"grade-assessments","Create a category, then add your first assessment.")
            }
            else -> {
                Toolbar {
                    Action("New category","layers",modifier=Modifier.testTag("new-grade-category")){c.editGradeCategory()}
                    Action("Edit",enabled=category!=null){category?.let {c.editGradeCategory(it)}}
                    Action("Delete",enabled=category!=null){if(category!=null&&c.gradeConfigurable())c.confirm("Delete category?",category.name()+"\nCategories containing assessments cannot be deleted."){c.mutate {c.services.academic.grades().deleteCategory(book.offeringId(),category.id())}}}
                    Action("Grading scale",modifier=Modifier.testTag("grading-scale")){c.editGradingScale()}
                }
                RecordTable(book.categories(),listOf(TableColumn("Category",2f){v:AssessmentCategory->v.name()},TableColumn("Weight %"){it.weight().toPlainString()}),AssessmentCategory::id,categoryId,{categoryId=it.id()},{c.editGradeCategory(it)},"grade-categories","No categories yet. Create categories with weights totaling 100%.")
                val s=book.scale()
                Copy("Inclusive thresholds · A ${s.a()} · B ${s.b()} · C ${s.c()} · D ${s.d()} · Pass ${s.pass()}%",13)
                Copy("Within each category, earned marks are divided by total maximum marks. Zero-weight categories do not affect totals. Final letters and pass/fail require all weighted entries and weights totaling 100%.",13,Ink.muted)
            }
        }
        c.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("grade-error"))}
    }
}

/** A bounded, virtualized sheet; keyed editors retain focus while calculated summaries update. */
@Composable private fun GradeGrid(editor:GradebookEditor,summary:GradeSummary,query:String,enabled:Boolean) {
    val book=editor.book
    var sort by remember {mutableStateOf("number")};var descending by remember {mutableStateOf(false)}
    val filtered=book.students().filter {("${it.student().studentNumber()} ${it.student().fullName()}").contains(query.trim(),true)}
    // Re-sort on an explicit header/filter change, not every keystroke (which could move the focused row).
    val rows=remember(editor,query,sort,descending) {filtered.sortedWith(Comparator {a,b->
        val compared=when(sort) {
            "name" -> a.student().fullName().compareTo(b.student().fullName(),true)
            "number" -> a.student().studentNumber().compareTo(b.student().studentNumber(),true)
            "result" -> compareValues(summary.results[a.enrollment().id()]?.weightedPoints(),summary.results[b.enrollment().id()]?.weightedPoints())
            else -> compareValues(editor.value(GradeKey(a.enrollment().id(),sort.toLong())).toBigDecimalOrNull(),editor.value(GradeKey(b.enrollment().id(),sort.toLong())).toBigDecimalOrNull())
        };if(descending)-compared else compared
    })}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val horizontal=rememberScrollState();val vertical=rememberLazyListState();val scope=rememberCoroutineScope()
    val reduced=LocalReducedMotion.current;val density=androidx.compose.ui.platform.LocalDensity.current
    val assessments=book.assessments()
    val cellWidth=((maxWidth-500.dp)/assessments.size.coerceAtLeast(1)).coerceAtLeast(150.dp)
    val width=260.dp+cellWidth*assessments.size+240.dp
    val focus=remember(rows.map {it.enrollment().id()},assessments.map {it.id()}) {rows.associate {r->r.enrollment().id() to assessments.associate {it.id() to FocusRequester()}}}
    fun navigate(row:Int,col:Int):Boolean {
        if(row !in rows.indices || col !in assessments.indices)return false
        scope.launch {
            vertical.scrollToItem(row)
            val x=with(density){(cellWidth*col).roundToPx()}
            if(reduced)horizontal.scrollTo(x) else horizontal.animateScrollTo(x)
            // scrollToItem composes the requested lazy row before the focus request.
            focus[rows[row].enrollment().id()]?.get(assessments[col].id())?.requestFocus()
        };return true
    }
    @Composable fun heading(title:String,key:String,width:Dp) {
        Box(Modifier.width(width).height(72.dp).clickable {if(sort==key)descending=!descending else {sort=key;descending=false}}.padding(12.dp)) {
            Copy(title+if(sort==key)if(descending)" ↓" else " ↑" else "",12,Ink.muted,lines=2)
        }
    }
    Column(Modifier.fillMaxWidth().border(1.dp,Ink.border,RoundedCornerShape(10.dp)).testTag("grade-grid")) {
        Column(Modifier.horizontalScroll(horizontal)) {
            Row(Modifier.width(width).background(Ink.raised)) {
                heading("Student number","number",115.dp);heading("Student","name",145.dp)
                assessments.forEach {heading("${it.title()}\n/ ${it.maximumMarks()}",it.id().toString(),cellWidth)}
                heading("Weighted /100 · Result","result",240.dp)
            }
            if(rows.isEmpty()) Copy("No enrolled students match. Enroll students in this offering or clear your search.",14,Ink.muted,modifier=Modifier.width(width).padding(24.dp))
            else LazyColumn(Modifier.width(width).heightIn(max=330.dp),state=vertical) {
                itemsIndexed(rows,key={_,r->r.enrollment().id()}) {index,row->
                    Row(Modifier.width(width).height(66.dp).background(if(index%2==0)Ink.panel else Ink.background),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Copy(row.student().studentNumber(),13,modifier=Modifier.width(115.dp).padding(12.dp))
                        Column(Modifier.width(145.dp).padding(10.dp)) {Copy(row.student().fullName(),13,lines=1);Copy(row.enrollment().status().name,10,Ink.muted)}
                        assessments.forEachIndexed {column,a->
                            val key=GradeKey(row.enrollment().id(),a.id());val value=editor.value(key)
                            var focused by remember {mutableStateOf(false)}
                            val invalid=try {GradebookService.validateScore(value,a);false}catch(_:DomainValidationException){true}
                            Box(Modifier.width(cellWidth).padding(6.dp)) {
                                BasicTextField(value,{editor.set(key,it)},singleLine=true,readOnly=!enabled,
                                    textStyle=TextStyle(color=Ink.text,fontFamily=fontFor(value),fontSize=14.sp),cursorBrush=SolidColor(Ink.violet),
                                    modifier=Modifier.fillMaxWidth().focusRequester(focus.getValue(row.enrollment().id()).getValue(a.id()))
                                        .onFocusChanged {focused=it.isFocused}.onPreviewKeyEvent {event->
                                            if(!enabled||event.type!=KeyEventType.KeyDown)false else when(event.key) {
                                                Key.Enter -> navigate(index+if(event.isShiftPressed)-1 else 1,column)
                                                Key.DirectionDown -> navigate(index+1,column)
                                                Key.DirectionUp -> navigate(index-1,column)
                                                Key.Tab -> {val next=index*assessments.size+column+if(event.isShiftPressed)-1 else 1; if(next<0)false else navigate(next/assessments.size,next%assessments.size)}
                                                else -> false
                                            }
                                        }.background(if(focused)Ink.selected else Ink.background,RoundedCornerShape(6.dp))
                                        .border(1.dp,if(focused||invalid)Ink.violet else Ink.border,RoundedCornerShape(6.dp)).padding(10.dp)
                                        .testTag("grade-cell-${key.enrollmentId()}-${key.assessmentId()}")
                                        .semantics {contentDescription="${row.student().fullName()} · ${a.title()} · out of ${a.maximumMarks()}. Blank means ungraded.";if(invalid)error("Score must be between zero and ${a.maximumMarks()}, with at most two decimal places.")},
                                    decorationBox={inner->Box {if(value.isEmpty())Copy("Ungraded",12,Ink.muted);inner()}})
                            }
                        }
                        val result=summary.results[row.enrollment().id()]
                        Column(Modifier.width(240.dp).padding(12.dp).testTag("grade-result-${row.enrollment().id()}")) {
                            Copy(result?.let {"${it.weightedPoints()}% · ${it.letter()} · ${it.outcome()}"}?:"Check scores",13,if(result?.complete()==true)Ink.positive else Ink.muted)
                            if(result?.complete()==false)Copy("${result.graded()}/${result.required()} graded · provisional",11,Ink.muted)
                        }
                    };Rule()
                }
            }
        }
        HorizontalScrollbar(rememberScrollbarAdapter(horizontal),Modifier.fillMaxWidth().height(12.dp))
    }
    }
}

@Composable internal fun GradeBulkOverlay(c:WorkspaceController,bulk:GradeBulk) {
    Modal("Bulk grade entry",c::closeEditor,submit={c.applyGradeBulk()},footer={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            c.error?.let {Copy(it,13,Ink.violet,modifier=Modifier.testTag("bulk-error"))}
            Action("Apply to gradebook","check",modifier=Modifier.testTag("apply-grade-bulk")){c.applyGradeBulk()}
        }
    }) {
        Select("Assessment",bulk.assessment,bulk.assessments.map {Choice(it.id().toString(),it.title())},{bulk.assessment=it})
        Input("Student number TAB score",bulk.text,{bulk.text=it},multiline=true,tag="grade-bulk-values")
        Copy("Paste one student number, a tab, and a score per line. Preserve leading zeros. A blank score clears a grade. Apply stages all valid rows; Save grades persists them together.",13,Ink.muted)
    }
}

internal fun WorkspaceController.exportGradeWorkbook() {
    if(!gradeConfigurable())return
    val book=gradebook!!.book
    confirm("Export saved gradebook?",GradeCalculator.configurationMessage(book)+"\nOnly saved grades are exported. Blank scores stay blank; incomplete results remain incomplete.") {
        task({
            var path:Path?=null
            EventQueue.invokeAndWait {
                val picker=FileDialog(null as Frame?,"Save gradebook as a new .xlsx file",FileDialog.SAVE)
                try {picker.file="Gradebook.xlsx";picker.isVisible=true;picker.file?.let {path=Path.of(picker.directory,it)}}finally{picker.dispose()}
            }
            path?.let {destination->
                val a=services.academic;val exporter=ExcelExportService(a.grades(),a.offerings(),a.attendance())
                exporter.save(exporter.render(book.offeringId()),destination)
            };path
        }) {path->if(path!=null)detail=Detail("Excel report saved",path.toString())}
    }
}
