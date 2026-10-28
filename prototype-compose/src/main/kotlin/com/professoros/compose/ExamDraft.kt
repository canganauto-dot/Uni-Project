package com.professoros.compose

import androidx.compose.runtime.*
import com.professoros.model.*
import java.math.BigDecimal

/** Local composition only. Existing Java services validate/save and preserve saved snapshots. */
class ExamDraft(val offeringId:Long,val existing:Exam?,val bank:List<Question>) {
    var title by mutableStateOf(existing?.title().orEmpty())
    var date by mutableStateOf(existing?.date()?.toString().orEmpty())
    var duration by mutableStateOf(existing?.durationMinutes()?.toString()?:"60")
    var intended by mutableStateOf(existing?.intendedMarks()?.toPlainString()?:"100")
    var instructions by mutableStateOf(existing?.instructions().orEmpty())
    val selected=mutableStateListOf<ExamQuestion>().apply {addAll(existing?.questions().orEmpty())}
    val marks=mutableStateMapOf<Long,String>()
    private val original=values()
    private fun values()=listOf(title,date,duration,intended,instructions,selected.map {it.questionId() to (marks[it.questionId()]?:it.marks().toPlainString())})
    val dirty get()=values()!=original
    val total get()=selected.fold(BigDecimal.ZERO){sum,q->sum+(marks[q.questionId()]?.toBigDecimalOrNull()?:if(marks.containsKey(q.questionId()))BigDecimal.ZERO else q.marks())}
    val mismatch get()=intended.toBigDecimalOrNull()?.compareTo(total)!=0
    fun add(question:Question) {
        if(selected.none {it.questionId()==question.id()})selected.add(existing?.questions()?.firstOrNull {it.questionId()==question.id()}?:ExamQuestion.snapshot(question,selected.size+1))
    }
    fun move(index:Int,delta:Int) {val to=index+delta;if(index in selected.indices && to in selected.indices){val q=selected.removeAt(index);selected.add(to,q)}}
    fun replace(questions:List<Question>) {selected.clear();questions.forEach(::add)}
    fun marksForSave():Map<Long,BigDecimal> = selected.associate {q->
        val value=(marks[q.questionId()]?:q.marks().toPlainString()).toBigDecimalOrNull()
        q.questionId() to com.professoros.service.ExamService.validateQuestionMarks(value)
    }
    fun input()=ExamInput(title,try {java.time.LocalDate.parse(date.trim())}catch(_:java.time.format.DateTimeParseException){null},duration.trim().toIntOrNull(),intended.trim().toBigDecimalOrNull(),instructions)
}
