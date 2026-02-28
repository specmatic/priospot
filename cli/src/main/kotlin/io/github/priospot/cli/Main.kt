package io.github.priospot.cli

import io.github.priospot.engine.PriospotConfig
import io.github.priospot.engine.PriospotEngine
import io.github.priospot.model.ModelJson
import io.github.priospot.model.PanopticodeDocument
import io.github.priospot.report.svg.ReportType
import io.github.priospot.report.svg.SvgTreemapReporter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolute

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    when (args.first()) {
        "analyze" -> runAnalyze(args.drop(1))
        "report" -> runReport(args.drop(1))
        else -> printUsage()
    }
}

private fun runAnalyze(args: List<String>) {
    val opts = parseOptions(args)
    val required = listOf("project-name", "source-roots", "output-json")
    required.forEach { key ->
        require(opts.containsKey(key)) { "Missing required option --$key" }
    }
    require(opts.containsKey("coverage-report") || opts.containsKey("coverage-reports")) {
        "Missing required option --coverage-report or --coverage-reports"
    }
    require(opts.containsKey("complexity-report") || opts.containsKey("complexity-reports")) {
        "Missing required option --complexity-report or --complexity-reports"
    }

    val basePath = Path.of(".").absolute().normalize()
    val outputPath = Path.of(opts.getValue("output-json"))
    val outputDir = outputPath.parent ?: Path.of(".")
    val coverageReports = when {
        opts.containsKey("coverage-reports") -> opts.getValue("coverage-reports")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { basePath.resolve(it) }
        else -> listOf(basePath.resolve(opts.getValue("coverage-report")))
    }
    val complexityReports = when {
        opts.containsKey("complexity-reports") -> opts.getValue("complexity-reports")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { basePath.resolve(it) }
        else -> listOf(basePath.resolve(opts.getValue("complexity-report")))
    }

    val result = PriospotEngine().run(
        PriospotConfig(
            projectName = opts.getValue("project-name"),
            projectVersion = opts["project-version"],
            sourceRoots = opts.getValue("source-roots").split(',').map { basePath.resolve(it.trim()) },
            coverageReports = coverageReports,
            complexityReports = complexityReports,
            churnDays = opts["churn-days"]?.toInt() ?: 30,
            churnLog = opts["churn-log"]?.let { basePath.resolve(it) },
            outputDir = outputDir,
            emitCompatibilityXml = opts["emit-compat-xml"]?.toBooleanStrictOrNull() ?: false,
            basePath = basePath
        )
    )

    if (result.panopticodeJson != outputPath) {
        Files.createDirectories(outputPath.parent)
        Files.copy(result.panopticodeJson, outputPath, StandardCopyOption.REPLACE_EXISTING)
    }

    println("Generated ${result.panopticodeJson}")
}

private fun runReport(args: List<String>) {
    val opts = parseOptions(args)
    val required = listOf("input-json", "type", "output-svg")
    required.forEach { key ->
        require(opts.containsKey(key)) { "Missing required option --$key" }
    }

    val doc = ModelJson.mapper.readValue(
        Files.readString(Path.of(opts.getValue("input-json"))),
        PanopticodeDocument::class.java
    )
    val type = when (opts.getValue("type").lowercase()) {
        "priospot" -> ReportType.PRIOSPOT
        "coverage" -> ReportType.COVERAGE
        "complexity" -> ReportType.COMPLEXITY
        "churn" -> ReportType.CHURN
        else -> error("Unsupported report type")
    }
    SvgTreemapReporter().generateInteractiveTreemap(doc.project, type, Path.of(opts.getValue("output-svg")))
    println("Generated ${opts.getValue("output-svg")}")
}

private fun parseOptions(args: List<String>): Map<String, String> {
    val map = linkedMapOf<String, String>()
    var idx = 0
    while (idx < args.size) {
        val token = args[idx]
        require(token.startsWith("--")) { "Unexpected token: $token" }
        val key = token.removePrefix("--")
        val value = args.getOrNull(idx + 1) ?: error("Missing value for $token")
        map[key] = value
        idx += 2
    }
    return map
}

private fun printUsage() {
    println(
        """
        Usage:
          priospot analyze --project-name <string> --source-roots <csv> (--coverage-report <path> | --coverage-reports <csv>) (--complexity-report <path> | --complexity-reports <csv>) --output-json <path> [--project-version <string>] [--churn-days <int>] [--churn-log <path>] [--emit-compat-xml <true|false>]
          priospot report --input-json <path> --type <priospot|coverage|complexity|churn> --output-svg <path>
        """.trimIndent()
    )
}
