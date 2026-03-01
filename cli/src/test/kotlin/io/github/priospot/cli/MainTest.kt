package io.github.priospot.cli

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import io.github.priospot.model.ModelJson
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.PanopticodeDocument
import io.github.priospot.model.RatioMetric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class MainTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `prints usage when no args are provided`() {
        val out = captureStdout { assertThat(runCli(emptyArray())).isEqualTo(0) }
        assertThat(out).contains("Usage:")
    }

    @Test
    fun `analyze supports multi-report args and writes priospot json`() {
        val sourceRoot = tempDir.resolve("src/main/kotlin")
        Files.createDirectories(sourceRoot)
        val sourceFile = sourceRoot.resolve("Sample.kt")
        Files.writeString(sourceFile, "package demo\nclass Sample { fun ok() = 1 }\n")

        val sourceFilePath = sourceFile.fileName.toString()
        val coverage1 = tempDir.resolve("coverage-1.json")
        val coverage2 = tempDir.resolve("coverage-2.json")
        Files.writeString(
            coverage1,
            """
            {
              "schemaVersion": 1,
              "generator": "coverageReport",
              "generatedAt": "2026-02-28T00:00:00Z",
              "files": [
                { "path": "$sourceFilePath", "lineCoverage": { "covered": 3, "total": 5 } }
              ]
            }
            """.trimIndent()
        )
        Files.writeString(
            coverage2,
            """
            {
              "schemaVersion": 1,
              "generator": "coverageReport",
              "generatedAt": "2026-02-28T00:00:00Z",
              "files": [
                { "path": "$sourceFilePath", "lineCoverage": { "covered": 2, "total": 5 } }
              ]
            }
            """.trimIndent()
        )

        val complexity1 = tempDir.resolve("complexity-1.json")
        val complexity2 = tempDir.resolve("complexity-2.json")
        Files.writeString(
            complexity1,
            """[{"path":"$sourceFilePath","ncss":7,"maxCcn":2}]"""
        )
        Files.writeString(
            complexity2,
            """[{"path":"$sourceFilePath","ncss":11,"maxCcn":4}]"""
        )

        val outputJson = tempDir.resolve("out/priospot.json")
        assertThat(
            runCli(
            arrayOf(
                "analyze",
                "--project-name", "cli-test",
                "--source-roots", normalize(sourceRoot.toAbsolutePath().toString()),
                "--coverage-reports", listOf(coverage1, coverage2).joinToString(",") {
                    normalize(it.toAbsolutePath().toString())
                },
                "--complexity-reports", listOf(complexity1, complexity2).joinToString(",") {
                    normalize(it.toAbsolutePath().toString())
                },
                "--output-json", normalize(outputJson.toAbsolutePath().toString())
            )
            )
        ).isEqualTo(0)

        assertThat(Files.exists(outputJson)).isTrue()
        val doc = ModelJson.mapper.readValue(Files.readString(outputJson), PanopticodeDocument::class.java)
        val file = doc.project.files.single()
        val maxCcn = file.metrics.first { it.name == "MAX-CCN" } as IntegerMetric
        val ncss = file.metrics.first { it.name == "NCSS" } as IntegerMetric
        val coverage = file.metrics.first { it.name == "Line Coverage" } as RatioMetric
        assertThat(maxCcn.value).isEqualTo(4)
        assertThat(ncss.value).isEqualTo(18)
        assertThat(coverage.numerator).isEqualTo(5.0)
        assertThat(coverage.denominator).isEqualTo(10.0)
    }

    @Test
    fun `report generates svg from priospot json`() {
        val sourceRoot = tempDir.resolve("src/main/kotlin")
        Files.createDirectories(sourceRoot)
        val sourceFile = sourceRoot.resolve("ReportMe.kt")
        Files.writeString(sourceFile, "class ReportMe\n")
        val sourceFilePath = sourceFile.fileName.toString()

        val coverage = tempDir.resolve("coverage.json")
        val complexity = tempDir.resolve("complexity.json")
        Files.writeString(
            coverage,
            """
            {
              "schemaVersion": 1,
              "generator": "coverageReport",
              "generatedAt": "2026-02-28T00:00:00Z",
              "files": [
                { "path": "$sourceFilePath", "lineCoverage": { "covered": 1, "total": 1 } }
              ]
            }
            """.trimIndent()
        )
        Files.writeString(
            complexity,
            """[{"path":"$sourceFilePath","ncss":3,"maxCcn":1}]"""
        )

        val outputJson = tempDir.resolve("out/priospot.json")
        val outputSvg = tempDir.resolve("out/priospot-interactive-treemap.svg")

        assertThat(
            runCli(
            arrayOf(
                "analyze",
                "--project-name", "cli-report-test",
                "--source-roots", normalize(sourceRoot.toAbsolutePath().toString()),
                "--coverage-report", normalize(coverage.toAbsolutePath().toString()),
                "--complexity-report", normalize(complexity.toAbsolutePath().toString()),
                "--output-json", normalize(outputJson.toAbsolutePath().toString())
            )
            )
        ).isEqualTo(0)
        assertThat(
            runCli(
            arrayOf(
                "report",
                "--input-json", normalize(outputJson.toAbsolutePath().toString()),
                "--type", "priospot",
                "--output-svg", normalize(outputSvg.toAbsolutePath().toString())
            )
            )
        ).isEqualTo(0)

        assertThat(Files.exists(outputSvg)).isTrue()
        val svg = Files.readString(outputSvg)
        assertThat(svg).contains("<svg")
        assertThat(svg).contains("Legend")
    }

    @Test
    fun `analyze fails when neither coverage-report nor coverage-reports is given`() {
        var exitCode = 0
        val err = captureStderr {
            val ex = try {
                runMain(
                    arrayOf(
                        "analyze",
                        "--project-name", "invalid",
                        "--source-roots", "src/main/kotlin",
                        "--complexity-report", "complexity.json",
                        "--output-json", "build/out.json"
                    )
                ) { code -> throw ExitCalled(code) }
                throw IllegalStateException("Expected ExitCalled")
            } catch (exception: Exception) {
                exception
            }
            assertThat(ex).isInstanceOf(ExitCalled::class)
            exitCode = (ex as ExitCalled).code
        }

        assertThat(exitCode).isEqualTo(-1)
        assertThat(err).contains("Missing required option --coverage-report or --coverage-reports")
    }

    private fun captureStdout(block: () -> Unit): String {
        val original = System.out
        val baos = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(baos))
            block()
        } finally {
            System.setOut(original)
        }
        return baos.toString()
    }

    private fun captureStderr(block: () -> Unit): String {
        val original = System.err
        val baos = ByteArrayOutputStream()
        try {
            System.setErr(PrintStream(baos))
            block()
        } finally {
            System.setErr(original)
        }
        return baos.toString()
    }

    private fun normalize(path: String): String = path.replace('\\', '/')
}

private class ExitCalled(val code: Int) : RuntimeException()
