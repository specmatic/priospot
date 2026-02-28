package io.github.priospot.engine

import io.github.priospot.model.ModelJson
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.PanopticodeDocument
import io.github.priospot.model.RatioMetric
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

    @Test
    fun `defaults missing coverage to full for test sources`() {
        val mainDir = tempDir.resolve("src/main/kotlin/com/example")
        val testDir = tempDir.resolve("src/test/kotlin/com/example")
        Files.createDirectories(mainDir)
        Files.createDirectories(testDir)
        Files.writeString(mainDir.resolve("MainFile.kt"), "class MainFile")
        Files.writeString(testDir.resolve("MainFileTest.kt"), "class MainFileTest")

        val churnLog = tempDir.resolve("gitlog.txt")
        Files.writeString(
            churnLog,
            """
            --x--2026-01-01--a
            1\t0\tsrc/main/kotlin/com/example/MainFile.kt
            1\t0\tsrc/test/kotlin/com/example/MainFileTest.kt
            """.trimIndent()
        )

        val output = tempDir.resolve("out-test-defaults")
        val result = PriospotEngine().run(
            PriospotConfig(
                projectName = "sample",
                sourceRoots = listOf(tempDir.resolve("src/main/kotlin"), tempDir.resolve("src/test/kotlin")),
                coverageReports = emptyList(),
                complexityReports = emptyList(),
                churnLog = churnLog,
                outputDir = output,
                deterministicTimestamp = "2026-01-01T00:00:00Z",
                basePath = tempDir
            )
        )

        val doc = ModelJson.mapper.readValue(Files.readString(result.panopticodeJson), PanopticodeDocument::class.java)
        val mainFile = doc.project.files.first { it.path.endsWith("MainFile.kt") }
        val testFile = doc.project.files.first { it.path.endsWith("MainFileTest.kt") }
        val mainCoverage = mainFile.metrics.first { it.name == "Line Coverage" } as RatioMetric
        val testCoverage = testFile.metrics.first { it.name == "Line Coverage" } as RatioMetric

        assertEquals(0.0, mainCoverage.numerator)
        assertEquals(1.0, mainCoverage.denominator)
        assertEquals(1.0, testCoverage.numerator)
        assertEquals(1.0, testCoverage.denominator)
    }

    @Test
    fun `derives complexity from kotlin source when report entry is missing`() {
        val sourceDir = tempDir.resolve("src/main/kotlin/com/example")
        Files.createDirectories(sourceDir)
        Files.writeString(
            sourceDir.resolve("Calc.kt"),
            """
            package com.example

            class Calc {
                fun score(input: Int): Int {
                    if (input > 0 && input < 10) {
                        return input
                    }
                    return 0
                }
            }
            """.trimIndent()
        )

        val churnLog = tempDir.resolve("gitlog.txt")
        Files.writeString(
            churnLog,
            """
            --x--2026-01-01--a
            1\t0\tsrc/main/kotlin/com/example/Calc.kt
            """.trimIndent()
        )

        val output = tempDir.resolve("out-source-complexity")
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

        val doc = ModelJson.mapper.readValue(Files.readString(result.panopticodeJson), PanopticodeDocument::class.java)
        val file = doc.project.files.single { it.path.endsWith("Calc.kt") }
        val maxCcn = file.metrics.first { it.name == "MAX-CCN" } as IntegerMetric
        val ncss = file.metrics.first { it.name == "NCSS" } as IntegerMetric
        assertEquals(3, maxCcn.value)
        assertTrue(ncss.value > 0)
    }
}
