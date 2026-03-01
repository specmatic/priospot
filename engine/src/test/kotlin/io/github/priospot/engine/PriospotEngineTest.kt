package io.github.priospot.engine

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.ModelJson
import io.github.priospot.model.PanopticodeDocument
import io.github.priospot.model.RatioMetric
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

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
        val config =
            PriospotConfig(
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

        assertThat(Files.exists(result.priospotJson)).isTrue()
        assertThat(result.reportPaths.size).isEqualTo(4)
        assertThat(Files.exists(output.resolve("priospot-interactive-treemap.svg"))).isTrue()
        assertThat(Files.exists(output.resolve("coverage-interactive-treemap.svg"))).isTrue()
        assertThat(Files.exists(output.resolve("complexity-interactive-treemap.svg"))).isTrue()
        assertThat(Files.exists(output.resolve("churn-interactive-treemap.svg"))).isTrue()
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
        val result =
            PriospotEngine().run(
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

        val json = Files.readString(result.priospotJson)
        assertThat(json).contains("\"name\" : \"Line Coverage\"")
        assertThat(json).contains("\"name\" : \"MAX-CCN\"")
        assertThat(json).contains("\"name\" : \"C3 Indicator\"")
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
        val result =
            PriospotEngine().run(
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

        val doc = ModelJson.mapper.readValue(Files.readString(result.priospotJson), PanopticodeDocument::class.java)
        val mainFile = doc.project.files.first { it.path.endsWith("MainFile.kt") }
        val testFile = doc.project.files.first { it.path.endsWith("MainFileTest.kt") }
        val mainCoverage = mainFile.metrics.first { it.name == "Line Coverage" } as RatioMetric
        val testCoverage = testFile.metrics.first { it.name == "Line Coverage" } as RatioMetric

        assertThat(mainCoverage.numerator).isEqualTo(0.0)
        assertThat(mainCoverage.denominator).isEqualTo(1.0)
        assertThat(testCoverage.numerator).isEqualTo(1.0)
        assertThat(testCoverage.denominator).isEqualTo(1.0)
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
        val result =
            PriospotEngine().run(
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

        val doc = ModelJson.mapper.readValue(Files.readString(result.priospotJson), PanopticodeDocument::class.java)
        val file = doc.project.files.single { it.path.endsWith("Calc.kt") }
        val maxCcn = file.metrics.first { it.name == "MAX-CCN" } as IntegerMetric
        val ncss = file.metrics.first { it.name == "NCSS" } as IntegerMetric
        assertThat(maxCcn.value).isEqualTo(3)
        assertThat(ncss.value).isGreaterThan(0)
    }
}
