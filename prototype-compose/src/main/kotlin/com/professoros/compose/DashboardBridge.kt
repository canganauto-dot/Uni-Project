package com.professoros.compose

import com.professoros.database.Database
import com.professoros.model.*
import com.professoros.repository.CourseRepository
import com.professoros.service.*
import java.nio.file.Path

/** Direct JVM calls into the unchanged Java application layer; no SQL, HTTP or copied business rules. */
class DashboardBridge(val databasePath: Path) {
    private val database = Database(databasePath)
    private val academic = AcademicServices.create(database)
    private val courses = CourseService(CourseRepository(database))
    private val dashboard = DashboardService(academic)
    fun initialize() = database.initialize()
    fun load(offeringId: Long?): DashboardSnapshot {
        val offerings = academic.offerings().summaries().sortedBy { it.offering().id() }
        val selected = offerings.firstOrNull { it.offering().id() == offeringId } ?: offerings.firstOrNull()
        return DashboardSnapshot(courses.findAll().size, offerings, selected, dashboard.load(selected?.offering()?.id()),
            selected?.let { AttendanceCalculator.course(academic.attendance().load(it.offering().id())) })
    }
}
data class DashboardSnapshot(val courses: Int, val offerings: List<OfferingSummary>, val selected: OfferingSummary?,
                             val overview: DashboardOverview, val attendance: AttendanceSummary?)
