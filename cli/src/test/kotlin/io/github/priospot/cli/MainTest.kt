package io.github.priospot.cli

import io.github.priospot.model.ModelJson
import io.github.priospot.model.PanopticodeDocument
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MainTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `prints usage when no args are provided`() {
        val out = captureStdout { main(emptyArray()) }
        assertTrue(out.contains("Usage:"))
    }

    @Test
    fun `analyze supports multi-report args and writes priospot json`() {
        val base = Path.of(".").toAbsolutePath().normalize()
        val sourceRoot = tempDir.resolve("src/main/kotlin")
        Files.createDirectories(sourceRoot)
        val sourceFile = sourceRoot.resolve("Sample.kt")
        Files.writeString(sourceFile, "package demo\nclass Sample { fun ok() = 1 }\n")

        val relativeFilePath = normalize(base.relativize(sourceFile).toString())
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
                { "path": "$relativeFilePath", "lineCoverage": { "covered": 3, "total": 5 } }
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
                { "path": "$relativeFilePath", "lineCoverage": { "covered": 2, "total": 5 } }
              ]
            }
            """.trimIndent()
        )

        val complexity1 = tempDir.resolve("complexity-1.json")
        val complexity2 = tempDir.resolve("complexity-2.json")
        Files.writeString(
            complexity1,
            """[{"path":"$relativeFilePath","ncss":7,"maxCcn":2}]"""
        )
        Files.writeString(
            complexity2,
            """[{"path":"$relativeFilePath","ncss":11,"maxCcn":4}]"""
        )

        val outputJson = tempDir.resolve("out/priospot.json")
        main(
            arrayOf(
                "analyze",
                "--project-name", "cli-test",
                "--source-roots", normalize(base.relativize(sourceRoot).toString()),
                "--coverage-reports", listOf(coverage1, coverage2).joinToString(",") { normalize(base.relativize(it).toString()) },
                "--complexity-reports", listOf(complexity1, complexity2).joinToString(",") { normalize(base.relativize(it).toString()) },
                "--output-json", normalize(base.relativize(outputJson).toString())
            )
        )

        assertTrue(Files.exists(outputJson))
        val doc = ModelJson.mapper.readValue(Files.readString(outputJson), PanopticodeDocument::class.java)
        val file = doc.project.files.single()
        val maxCcn = file.metrics.first { it.name == "MAX-CCN" }
        val ncss = file.metrics.first { it.name == "NCSS" }
        val coverage = file.metrics.first { it.name == "Line Coverage" }
        assertTrue(maxCcn.toString().contains("value=4"))
        assertTrue(ncss.toString().contains("value=18"))
        assertTrue(coverage.toString().contains("numerator=5.0"))
        assertTrue(coverage.toString().contains("denominator=10.0"))
    }

    @Test
    fun `report generates svg from priospot json`() {
        val base = Path.of(".").toAbsolutePath().normalize()
        val sourceRoot = tempDir.resolve("src/main/kotlin")
        Files.createDirectories(sourceRoot)
        val sourceFile = sourceRoot.resolve("ReportMe.kt")
        Files.writeString(sourceFile, "class ReportMe\n")
        val relativeFilePath = normalize(base.relativize(sourceFile).toString())

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
                { "path": "$relativeFilePath", "lineCoverage": { "covered": 1, "total": 1 } }
              ]
            }
            """.trimIndent()
        )
        Files.writeString(
            complexity,
            """[{"path":"$relativeFilePath","ncss":3,"maxCcn":1}]"""
        )

        val outputJson = tempDir.resolve("out/priospot.json")
        val outputSvg = tempDir.resolve("out/priospot-interactive-treemap.svg")

        main(
            arrayOf(
                "analyze",
                "--project-name", "cli-report-test",
                "--source-roots", normalize(base.relativize(sourceRoot).toString()),
                "--coverage-report", normalize(base.relativize(coverage).toString()),
                "--complexity-report", normalize(base.relativize(complexity).toString()),
                "--output-json", normalize(base.relativize(outputJson).toString())
            )
        )
        main(
            arrayOf(
                "report",
                "--input-json", normalize(base.relativize(outputJson).toString()),
                "--type", "priospot",
                "--output-svg", normalize(base.relativize(outputSvg).toString())
            )
        )

        assertTrue(Files.exists(outputSvg))
        val svg = Files.readString(outputSvg)
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("Legend"))
    }

    @Test
    fun `analyze fails when neither coverage-report nor coverage-reports is given`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            main(
                arrayOf(
                    "analyze",
                    "--project-name", "invalid",
                    "--source-roots", "src/main/kotlin",
                    "--complexity-report", "complexity.json",
                    "--output-json", "build/out.json"
                )
            )
        }
        assertEquals("Missing required option --coverage-report or --coverage-reports", ex.message)
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

    private fun normalize(path: String): String = path.replace('\\', '/')
}
