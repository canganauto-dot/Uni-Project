package com.professoros.compose

import com.professoros.database.Database
import com.professoros.model.*
import com.professoros.repository.CourseRepository
import com.professoros.service.*
import java.nio.file.Path
import java.time.LocalDate

/** Synthetic records live only in an isolated test database, never in prototype startup. */
internal fun fixture(path: Path): DashboardBridge {
    val db=Database(path);db.initialize();val a=AcademicServices.create(db)
    val courses=CourseService(CourseRepository(db));val today=LocalDate.now()
    val term=a.terms().create(AcademicTermInput("Fall","2026–2027",today.minusMonths(2),today.plusMonths(4),AcademicStatus.ACTIVE))
    for ((index,name) in listOf("Human–Computer Interaction","هندسة البرمجيات","Data Structures & Algorithms").withIndex()) {
        val course=courses.create(CourseInput("CS${301+index}",name,"Computer Science",3,""))
        val offering=a.offerings().create(CourseOfferingInput(course.id(),term.id(),"A","",AcademicStatus.ACTIVE))
        if(index==0) {
            for(i in 1..5) a.lectures().create(offering.id(),LectureInput(i,listOf("Foundations of interaction","Human factors","Design systems","Interfaces that understand people","تجربة المستخدم وإمكانية الوصول")[i-1],today.plusDays((i-4).toLong()),"","",60,if(i<=3)LectureStatus.COMPLETED else LectureStatus.PLANNED,""))
            for(i in 1..12) {val student=a.students().create(StudentInput("00$i","Student","$i","",""));a.enrollments().enroll(student.id(),offering.id())}
        }
    }
    return DashboardBridge(path).also { it.initialize() }
}
