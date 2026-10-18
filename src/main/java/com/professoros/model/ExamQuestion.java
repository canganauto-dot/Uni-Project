package com.professoros.model;
import java.math.BigDecimal;
import java.util.List;
/** Frozen content, independent of later edits to the source question bank. */
public record ExamQuestion(long questionId, int position, QuestionType type, String text, String topic,
                           Difficulty difficulty, BigDecimal marks, String answer, String explanation,
                           String imagePath, List<AnswerChoice> choices) {
    public ExamQuestion { choices = List.copyOf(choices); }
    public static ExamQuestion snapshot(Question question, int position) {
        return new ExamQuestion(question.id(),position,question.type(),question.text(),question.topic(),question.difficulty(),
                question.marks(),question.answer(),question.explanation(),question.imagePath(),question.choices());
    }
    public ExamQuestion atPosition(int value) {
        return new ExamQuestion(questionId,value,type,text,topic,difficulty,marks,answer,explanation,imagePath,choices);
    }
}
