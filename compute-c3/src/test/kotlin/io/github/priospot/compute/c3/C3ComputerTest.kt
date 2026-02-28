package io.github.priospot.compute.c3

import io.github.priospot.model.FileEntry
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.Project
import io.github.priospot.model.RatioMetric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class C3ComputerTest {
    private val c3 = C3Computer()

    @Test
    fun `formula produces expected range`() {
        val project = Project(
            name = "p",
            version = null,
            basePath = "/tmp",
            files = listOf(
                FileEntry(
                    name = "Foo.kt",
                    path = "src/Foo.kt",
                    metrics = listOf(
                        IntegerMetric(MetricNames.TIMES_CHANGED, 3),
                        IntegerMetric(MetricNames.LINES_ADDED, 30),
                        IntegerMetric(MetricNames.LINES_REMOVED, 10),
                        IntegerMetric(MetricNames.MAX_CCN, 8),
                        RatioMetric(MetricNames.LINE_COVERAGE, 80.0, 100.0)
                    )
                )
            )
        )

        val result = c3.compute(project, churnDays = 30)

        val c3Metric = result.project.files.single().metrics.first { it.name == MetricNames.C3_INDICATOR }
        val value = (c3Metric as io.github.priospot.model.DecimalMetric).value
        assertTrue(value in 0.0..1.0)
        assertEquals(1, result.filesComputed)
    }

    @Test
    fun `skips file with missing metrics`() {
        val project = Project(
            name = "p",
            version = null,
            basePath = "/tmp",
            files = listOf(FileEntry(name = "Foo.kt", path = "src/Foo.kt"))
        )

        val result = c3.compute(project, churnDays = 30)

        assertEquals(0, result.filesComputed)
        assertTrue(result.warnings.isNotEmpty())
    }
}
