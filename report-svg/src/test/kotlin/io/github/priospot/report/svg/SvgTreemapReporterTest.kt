package io.github.priospot.report.svg

import assertk.assertThat
import assertk.assertions.contains
import io.github.priospot.model.DecimalMetric
import io.github.priospot.model.FileEntry
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.Project
import java.nio.file.Files
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

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
                    path = "src/main/kotlin/com/example/Foo.kt",
                    metrics = listOf(
                        IntegerMetric(MetricNames.NCSS, 10),
                        DecimalMetric(MetricNames.C3_INDICATOR, 0.75)
                    )
                ),
                FileEntry(
                    name = "FooTest.kt",
                    path = "src/test/kotlin/com/example/FooTest.kt",
                    metrics = listOf(
                        IntegerMetric(MetricNames.NCSS, 8),
                        DecimalMetric(MetricNames.C3_INDICATOR, 0.10)
                    )
                )
            )
        )

        val temp = createTempDirectory()
        val output = temp.resolve("out.svg")

        SvgTreemapReporter().generateInteractiveTreemap(project, ReportType.PRIOSPOT, output)

        val svg = Files.readString(output)
        assertThat(svg).contains("<svg")
        assertThat(svg).contains("<rect")
        assertThat(svg).contains("<title>")
        assertThat(svg).contains("id=\"view-all\"")
        assertThat(svg).contains("id=\"view-no-tests\"")
        assertThat(svg).contains("id=\"test-filter-checkbox\"")
        assertThat(svg).contains("toggleTestClasses()")
        assertThat(svg).contains("Show test classes")
    }
}
