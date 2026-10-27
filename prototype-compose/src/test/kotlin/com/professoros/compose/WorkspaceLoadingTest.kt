package com.professoros.compose

import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/** Runs IO continuations explicitly, without sleeps or coroutine-test dependencies. */
internal class QueuedIo : CoroutineDispatcher() {
    private val work = ArrayDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { work.addLast(block) }
    fun drain() {
        var steps = 0
        while (work.isNotEmpty()) {
            check(++steps < 100) { "Loading did not settle" }
            work.removeFirst().run()
        }
    }
}

class WorkspaceLoadingTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun selectionRequestedDuringLoadIsNotLost() {
        val path = folder.root.toPath().resolve("selection.db")
        fixture(path)
        val services = WorkspaceServices(path)
        val ids = services.academic.offerings().findAll().map { it.id() }
        val io = QueuedIo()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = WorkspaceController(services, scope, io)
            controller.select(ids[0])
            assertTrue(controller.busy)
            controller.select(ids[1])
            controller.select(ids[2])
            io.drain()
            assertFalse(controller.busy)
            assertEquals(ids[2], controller.offeringId)
            assertEquals(ids[2], controller.gradebook!!.book.offeringId())
            assertEquals(ids[2], controller.snapshot!!.selected!!.offering().id())
            controller.select(null); io.drain()
            assertNull(controller.offeringId)
            assertNull(controller.snapshot!!.selected)
            assertNull(controller.gradebook)
            controller.refresh(); io.drain()
            assertNull(controller.snapshot!!.selected)
        } finally { scope.cancel() }
    }

    @Test fun directSelectionProtectsAnUnsavedForm() {
        val path = folder.root.toPath().resolve("dirty.db")
        fixture(path)
        val services = WorkspaceServices(path)
        val ids = services.academic.offerings().findAll().map { it.id() }
        val io = QueuedIo()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = WorkspaceController(services, scope, io)
            controller.select(ids[0]); io.drain()
            controller.editCourse()
            controller.form!!.values["name"] = "Unsaved course"
            controller.select(ids[1])
            assertEquals(ids[0], controller.offeringId)
            assertNotNull(controller.confirmation)
            assertEquals("Unsaved course", controller.form!!["name"])
            controller.confirmation = null // Keep editing.
            assertEquals(ids[0], controller.snapshot!!.selected!!.offering().id())
            controller.select(ids[1])
            controller.confirmation!!.proceed(); controller.confirmation = null
            io.drain()
            assertEquals(ids[1], controller.offeringId)
            assertNull(controller.form)
        } finally { scope.cancel() }
    }

    @Test fun invalidQueuedSelectionDoesNotReplaceTheLatestValidRequest() {
        val path = folder.root.toPath().resolve("valid.db")
        fixture(path)
        val services = WorkspaceServices(path)
        val ids = services.academic.offerings().findAll().map { it.id() }
        val io = QueuedIo()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = WorkspaceController(services, scope, io)
            controller.select(ids[0])
            controller.select(ids[1])
            controller.select(Long.MAX_VALUE)
            io.drain()
            assertEquals(ids[1], controller.offeringId)
            assertEquals(ids[1], controller.snapshot!!.selected!!.offering().id())
        } finally { scope.cancel() }
    }

    @Test fun queuedSelectionWaitsForWritesAndProtectsDirtyGradesAndAttendance() {
        val path = folder.root.toPath().resolve("sheets.db")
        fixture(path)
        val services = WorkspaceServices(path)
        val ids = services.academic.offerings().findAll().map { it.id() }
        val category = services.academic.grades().saveCategory(ids[0], null, "Work", java.math.BigDecimal("100"))
        val assessment = services.academic.grades().saveAssessment(ids[0], null, category.id(), "Test", java.math.BigDecimal.TEN, java.time.LocalDate.now())
        val io = QueuedIo()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = WorkspaceController(services, scope, io)
            controller.select(ids[0]); io.drain()
            val enrollment = controller.roster.first().enrollment().id()
            val key = com.professoros.model.GradeKey(enrollment, assessment.id())
            controller.gradebook!!.set(key, "0")
            controller.attendance!!.set(enrollment, com.professoros.model.AttendanceStatus.PRESENT)
            var committed = false
            controller.task({ committed = true })
            controller.select(ids[1])
            io.drain()
            assertTrue(committed)
            assertEquals(ids[0], controller.offeringId)
            assertNotNull(controller.confirmation)
            assertEquals("0", controller.gradebook!!.value(key))
            assertTrue(controller.attendance!!.dirty)
            controller.confirmation!!.proceed(); controller.confirmation = null; io.drain()
            assertEquals(ids[1], controller.offeringId)
            assertTrue(services.academic.grades().load(ids[0]).grades().isEmpty())
            assertTrue(services.academic.attendance().load(ids[0]).records().isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun failedSelectionRetainsConsistentOldContextAndCanRetry() {
        val path = folder.root.toPath().resolve("failure.db")
        fixture(path)
        val services = WorkspaceServices(path)
        val ids = services.academic.offerings().findAll().map { it.id() }
        val io = QueuedIo()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = WorkspaceController(services, scope, io)
            controller.select(ids[0]); io.drain()
            val backup = path.resolveSibling("saved.db")
            java.nio.file.Files.move(path, backup)
            java.nio.file.Files.createDirectory(path)
            controller.select(ids[1]); io.drain()
            assertNotNull(controller.loadError)
            assertEquals(ids[0], controller.offeringId)
            assertEquals(ids[0], controller.snapshot!!.selected!!.offering().id())
            assertEquals(ids[0], controller.gradebook!!.book.offeringId())
            java.nio.file.Files.delete(path)
            java.nio.file.Files.move(backup, path)
            controller.select(ids[1]); io.drain()
            assertNull(controller.loadError)
            assertEquals(ids[1], controller.offeringId)
        } finally { scope.cancel() }
    }
}
