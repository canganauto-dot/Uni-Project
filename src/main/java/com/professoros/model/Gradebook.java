package com.professoros.model;
import java.util.List;
public record Gradebook(long offeringId,List<AssessmentCategory> categories,List<Assessment> assessments,
                        List<EnrolledStudent> students,List<StudentGrade> grades,GradingScale scale) {
    public Gradebook { categories=List.copyOf(categories); assessments=List.copyOf(assessments); students=List.copyOf(students); grades=List.copyOf(grades); }
}
