package io.github.priospot.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PriospotEngineTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `runs end to end and writes required outputs`() {
        val sourceDir = tempDir.resolve("src/main/kotlin/com/example")
        Files.createDirectories(sourceDir)
        Files.writeString(sourceDir.resolve("Foo.kt"), "class Foo")

        val coverageXml = tempDir.resolve("coverage.xml")
        Files.writeString(
            coverageXml,
            """
            <report>
              <package name="src/main/kotlin/com/example">
                <sourcefile name="Foo.kt">
                  <counter type="LINE" missed="10" covered="90"/>
                </sourcefile>
              </package>
            </report>
            """.trimIndent()
        )

        val complexityJson = tempDir.resolve("complexity.json")
        Files.writeString(
            complexityJson,
            """
            [
              {"path":"src/main/kotlin/com/example/Foo.kt","ncss":15,"maxCcn":4}
            ]
            """.trimIndent()
        )

        val churnLog = tempDir.resolve("gitlog.txt")
        Files.writeString(
            churnLog,
            """
            --x--2026-01-01--a
            5\t2\tsrc/main/kotlin/com/example/Foo.kt
            """.trimIndent()
        )

        val output = tempDir.resolve("out")
        val config = PriospotConfig(
            projectName = "sample",
            sourceRoots = listOf(tempDir.resolve("src/main/kotlin")),
            coverageReports = listOf(coverageXml),
            complexityReports = listOf(complexityJson),
            churnLog = churnLog,
            outputDir = output,
            deterministicTimestamp = "2026-01-01T00:00:00Z",
            basePath = tempDir
        )

        val result = PriospotEngine().run(config)

        assertTrue(Files.exists(result.panopticodeJson))
        assertEquals(4, result.reportPaths.size)
        assertTrue(Files.exists(output.resolve("priospot-interactive-treemap.svg")))
        assertTrue(Files.exists(output.resolve("coverage-interactive-treemap.svg")))
        assertTrue(Files.exists(output.resolve("complexity-interactive-treemap.svg")))
        assertTrue(Files.exists(output.resolve("churn-interactive-treemap.svg")))
    }

    @Test
    fun `applies default coverage and complexity when reports are absent`() {
        val sourceDir = tempDir.resolve("src/main/kotlin/com/example")
        Files.createDirectories(sourceDir)
        Files.writeString(sourceDir.resolve("Foo.kt"), "class Foo")

        val churnLog = tempDir.resolve("gitlog.txt")
        Files.writeString(
            churnLog,
            """
            --x--2026-01-01--a
            5\t2\tsrc/main/kotlin/com/example/Foo.kt
            """.trimIndent()
        )

        val output = tempDir.resolve("out-defaults")
        val result = PriospotEngine().run(
            PriospotConfig(
                projectName = "sample",
                sourceRoots = listOf(tempDir.resolve("src/main/kotlin")),
                coverageReports = emptyList(),
                complexityReports = emptyList(),
                churnLog = churnLog,
                outputDir = output,
                deterministicTimestamp = "2026-01-01T00:00:00Z",
                basePath = tempDir
            )
        )

        val json = Files.readString(result.panopticodeJson)
        assertTrue(json.contains("\"name\" : \"Line Coverage\""))
        assertTrue(json.contains("\"name\" : \"MAX-CCN\""))
        assertTrue(json.contains("\"name\" : \"C3 Indicator\""))
    }
}
