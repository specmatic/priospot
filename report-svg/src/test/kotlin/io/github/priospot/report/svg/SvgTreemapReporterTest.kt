package io.github.priospot.report.svg

import io.github.priospot.model.DecimalMetric
import io.github.priospot.model.FileEntry
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.Project
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class SvgTreemapReporterTest {
    @Test
    fun `writes parseable svg`() {
        val project = Project(
            name = "sample",
            version = null,
            basePath = "/tmp",
            files = listOf(
                FileEntry(
                    name = "Foo.kt",
                    path = "src/Foo.kt",
                    metrics = listOf(
                        IntegerMetric(MetricNames.NCSS, 10),
                        DecimalMetric(MetricNames.C3_INDICATOR, 0.75)
                    )
                )
            )
        )

        val temp = createTempDirectory()
        val output = temp.resolve("out.svg")

        SvgTreemapReporter().generateInteractiveTreemap(project, ReportType.PRIOSPOT, output)

        val svg = Files.readString(output)
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("<rect"))
        assertTrue(svg.contains("<title>"))
    }
}
