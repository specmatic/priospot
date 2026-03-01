package io.github.priospot.cli

import io.github.priospot.engine.PriospotConfig
import io.github.priospot.engine.PriospotEngine
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import kotlin.io.path.absolute

@CommandLine.Command(
    name = "analyze",
    description = [
        "Run end-to-end hotspot analysis and generate priospot JSON plus treemap reports."
    ],
)
class AnalyzeCommand : Callable<Int> {
    @CommandLine.Option(
        names = ["--project-name"],
        required = true,
        description = ["Logical project name to embed in the output model and reports."],
        paramLabel = "<name>",
    )
    lateinit var projectName: String

    @CommandLine.Option(
        names = ["--project-version"],
        description = ["Optional project version to include in the generated document."],
        paramLabel = "<version>",
    )
    var projectVersion: String? = null

    @CommandLine.Option(
        names = ["--source-roots"],
        required = true,
        split = ",",
        description = ["Comma-separated source roots relative to cwd (for example: src/main/kotlin,src/test/kotlin)."],
        paramLabel = "<path[,path...]>",
    )
    var sourceRoots: List<String> = emptyList()

    @CommandLine.Option(
        names = ["--coverage-report"],
        description = ["Single coverage report path. Use this or --coverage-reports."],
        paramLabel = "<path>",
    )
    var coverageReport: String? = null

    @CommandLine.Option(
        names = ["--coverage-reports"],
        split = ",",
        description = ["Comma-separated coverage report paths (merged during ingestion)."],
        paramLabel = "<path[,path...]>",
    )
    var coverageReports: List<String> = emptyList()

    @CommandLine.Option(
        names = ["--complexity-report"],
        description = ["Single complexity report path. Use this or --complexity-reports."],
        paramLabel = "<path>",
    )
    var complexityReport: String? = null

    @CommandLine.Option(
        names = ["--complexity-reports"],
        split = ",",
        description = ["Comma-separated complexity report paths (merged during ingestion)."],
        paramLabel = "<path[,path...]>",
    )
    var complexityReports: List<String> = emptyList()

    @CommandLine.Option(
        names = ["--output-json"],
        required = true,
        description = ["Output file for priospot JSON (for example: build/reports/priospot/priospot.json)."],
        paramLabel = "<path>",
    )
    lateinit var outputJson: String

    @CommandLine.Option(
        names = ["--churn-days"],
        description = ["Lookback window in days for churn calculation. Default: 30."],
        paramLabel = "<days>",
    )
    var churnDays: Int = 30

    @CommandLine.Option(
        names = ["--churn-log"],
        description = ["Optional precomputed churn log file; when omitted, churn is derived from git history."],
        paramLabel = "<path>",
    )
    var churnLog: String? = null

    @CommandLine.Option(
        names = ["--emit-compat-xml"],
        description = ["Also generate compatibility XML output alongside canonical JSON. Default: false."],
    )
    var emitCompatibilityXml: Boolean = false

    override fun call(): Int {
        require(coverageReport != null || coverageReports.isNotEmpty()) {
            "Missing required option --coverage-report or --coverage-reports"
        }
        require(complexityReport != null || complexityReports.isNotEmpty()) {
            "Missing required option --complexity-report or --complexity-reports"
        }

        val basePath = Path.of(".").absolute().normalize()
        val outputPath = Path.of(outputJson)
        val outputDir = outputPath.parent ?: Path.of(".")
        val normalizedSourceRoots = sourceRoots.map { basePath.resolve(it.trim()) }
        val resolvedCoverageReports =
            if (coverageReports.isNotEmpty()) {
                coverageReports.map { basePath.resolve(it.trim()) }
            } else {
                listOf(basePath.resolve(coverageReport!!.trim()))
            }
        val resolvedComplexityReports =
            if (complexityReports.isNotEmpty()) {
                complexityReports.map { basePath.resolve(it.trim()) }
            } else {
                listOf(basePath.resolve(complexityReport!!.trim()))
            }

        val result =
            PriospotEngine().run(
                PriospotConfig(
                    projectName = projectName,
                    projectVersion = projectVersion,
                    sourceRoots = normalizedSourceRoots,
                    coverageReports = resolvedCoverageReports,
                    complexityReports = resolvedComplexityReports,
                    churnDays = churnDays,
                    churnLog = churnLog?.let { basePath.resolve(it) },
                    outputDir = outputDir,
                    emitCompatibilityXml = emitCompatibilityXml,
                    basePath = basePath,
                ),
            )

        if (result.priospotJson != outputPath) {
            Files.createDirectories(outputPath.parent)
            Files.copy(result.priospotJson, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }

        println("Generated ${result.priospotJson}")
        return 0
    }
}
