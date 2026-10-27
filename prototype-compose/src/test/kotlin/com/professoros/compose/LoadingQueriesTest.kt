package com.professoros.compose

import com.professoros.model.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import kotlin.test.*

class LoadingQueriesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun batchQuestionReadKeepsOneSnapshotAcrossAConcurrentCommit() {
        val path = folder.root.toPath().resolve("snapshot.db")
        fixture(path)
        val services = WorkspaceServices(path)
        val course = services.catalog().courses.first()
        val original = services.academic.questions().create(QuestionInput(course.id(), QuestionType.MCQ,
            "Original prompt", "Topic", Difficulty.MEDIUM, BigDecimal.ONE, "", "", "",
            listOf(AnswerChoice("Original yes", true), AnswerChoice("Original no", false))))
        com.professoros.database.Database(path).openConnection().use { connection ->
            connection.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        }
        var committed = false
        SqlQueries(path).use { sql ->
            sql.afterSelect = { statement ->
                if (statement.contains("FROM question_choices")) {
                    sql.afterSelect = null
                    services.academic.questions().update(original.id(), QuestionInput(course.id(), QuestionType.MCQ,
                        "Changed prompt", "Topic", Difficulty.HARD, BigDecimal.TEN, "", "", "",
                        listOf(AnswerChoice("Changed no", false), AnswerChoice("Changed yes", true))))
                    committed = true
                }
            }
            assertEquals(listOf(original), services.academic.questions().findAll())
            assertTrue(committed)
        }
        val saved = services.academic.questions().findById(original.id())
        assertEquals("Changed prompt", saved.text())
        assertEquals(BigDecimal("10.00"), saved.marks())
        assertTrue(saved.choices()[1].correct())
    }

    @Test fun workspaceUsesOneBoundedReadAndSelectionReusesGlobalData() {
        val path = folder.root.toPath().resolve("queries.db")
        val bridge = fixture(path)
        val services = WorkspaceServices(path)
        val offerings = services.academic.offerings().findAll()
        val offering = offerings.first()
        repeat(20) { index ->
            services.academic.questions().create(QuestionInput(offering.courseId(), QuestionType.MCQ,
                "Question $index", "Topic", Difficulty.MEDIUM, BigDecimal.ONE, "", "", "",
                listOf(AnswerChoice("Yes", true), AnswerChoice("No", false))))
        }
        val io = QueuedIo()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            SqlQueries(path).use { sql ->
                val questions = services.academic.questions().findAll()
                assertEquals(20, questions.size)
                assertTrue(questions.all { it.choices().size == 2 && it.choices()[0].correct() })
                assertEquals(2, sql.selects.size)
                println("QUERY_MEASUREMENT questionBank=${sql.selects.size}")
                sql.selects.clear()

                val controller = WorkspaceController(services, scope, io)
                controller.refresh(); io.drain()
                println("QUERY_MEASUREMENT startup=${sql.selects.size}")
                assertTrue(sql.selects.size <= 21, "Startup query budget: ${sql.selects}")
                assertEquals(1, sql.selects.count { it.contains("FROM schema_version") })
                assertEquals(bridge.load(null), controller.snapshot)
                val globalQuestions = controller.questions
                val globalCatalog = controller.catalog
                sql.selects.clear()

                controller.refresh(); io.drain()
                println("QUERY_MEASUREMENT explicitRefresh=${sql.selects.size}")
                assertTrue(sql.selects.size <= 20, "Refresh query budget: ${sql.selects}")
                assertFalse(sql.selects.any { it.contains("schema_version") })
                assertEquals(bridge.load(offering.id()), controller.snapshot)
                sql.selects.clear()

                val latestQuestions = controller.questions
                val latestCatalog = controller.catalog
                controller.select(offerings.last().id()); io.drain()
                println("QUERY_MEASUREMENT selection=${sql.selects.size}")
                assertTrue(sql.selects.size <= 14, "Selection query budget: ${sql.selects}")
                assertFalse(sql.selects.any { it.contains("FROM questions") || it.contains("FROM question_choices") })
                assertSame(latestQuestions, controller.questions)
                assertSame(latestCatalog, controller.catalog)
                assertEquals(bridge.load(offerings.last().id()), controller.snapshot)
                assertEquals(20, globalQuestions.size)
                assertEquals(3, globalCatalog.courses.size)
                sql.selects.clear()

                controller.mutate { services.courses.create(CourseInput("ADDED", "New course", "", 3, "")) }
                io.drain()
                println("QUERY_MEASUREMENT courseMutation=${sql.selects.size}")
                assertTrue(sql.selects.size <= 21)
                assertEquals(4, controller.catalog.courses.size)
                assertEquals(bridge.load(offerings.last().id()), controller.snapshot)
                sql.selects.clear()

                // A larger bank still requires two SELECTs, rather than one additional query per MCQ.
                repeat(20) { index ->
                    services.academic.questions().create(QuestionInput(offering.courseId(), QuestionType.MCQ,
                        "More $index", "Topic", Difficulty.EASY, BigDecimal.ONE, "", "", "",
                        listOf(AnswerChoice("No", false), AnswerChoice("Yes", true))))
                }
                sql.selects.clear()
                assertEquals(40, services.academic.questions().findAll().size)
                assertEquals(2, sql.selects.size)
                controller.refresh(); io.drain()
                assertEquals(40, controller.questions.size) // Explicit refresh invalidates global data.
            }
        } finally { scope.cancel() }
    }
}
