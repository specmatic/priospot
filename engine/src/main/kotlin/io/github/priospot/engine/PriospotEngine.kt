package io.github.priospot.engine

import io.github.priospot.compat.xml.CompatXmlExporter
import io.github.priospot.compute.c3.C3Computer
import io.github.priospot.ingest.churn.ChurnImporter
import io.github.priospot.ingest.churn.ChurnOptions
import io.github.priospot.ingest.complexity.ComplexityImporter
import io.github.priospot.ingest.complexity.KotlinSourceComplexityAnalyzer
import io.github.priospot.ingest.coverage.CoverageImporter
import io.github.priospot.ingest.source.SourceInventoryBuilder
import io.github.priospot.model.ModelJson
import io.github.priospot.model.PanopticodeDocument
import io.github.priospot.model.RatioMetric
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.DecimalMetric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.nowIsoTimestamp
import io.github.priospot.model.sortedDeterministic
import io.github.priospot.model.sortedDeep
import io.github.priospot.model.normalizePath
import io.github.priospot.report.svg.ReportType
import io.github.priospot.report.svg.SvgTreemapReporter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.InvalidPathException
import kotlin.system.measureTimeMillis

data class PriospotConfig(
    val projectName: String,
    val projectVersion: String? = null,
    val sourceRoots: List<Path>,
    val coverageReports: List<Path> = emptyList(),
    val complexityReports: List<Path> = emptyList(),
    val defaultCoverageNumerator: Double = 0.0,
    val defaultCoverageDenominator: Double = 1.0,
    val defaultMaxCcn: Int = 1000,
    val churnDays: Int = 30,
    val churnLog: Path? = null,
    val outputDir: Path,
    val emitCompatibilityXml: Boolean = false,
    val deterministicTimestamp: String? = null,
    val coverageTask: String? = null,
    val complexityTask: String? = null,
    val basePath: Path
)

data class StageTiming(
    val stage: String,
    val millis: Long
)

data class PriospotResult(
    val panopticodeJson: Path,
    val reportPaths: Map<ReportType, Path>,
    val stageTimings: List<StageTiming>,
    val diagnostics: List<String>,
    val summary: Map<String, Int>
)

class PriospotEngine {
    private val sourceInventoryBuilder = SourceInventoryBuilder()
    private val churnImporter = ChurnImporter()
    private val coverageImporter = CoverageImporter()
    private val complexityImporter = ComplexityImporter()
    private val kotlinSourceComplexityAnalyzer = KotlinSourceComplexityAnalyzer()
    private val c3Computer = C3Computer()
    private val svgTreemapReporter = SvgTreemapReporter()
    private val compatXmlExporter = CompatXmlExporter()

    fun run(config: PriospotConfig): PriospotResult {
        require(config.sourceRoots.isNotEmpty()) { "sourceRoots must not be empty" }
        val coverageReports = config.coverageReports.filter { report ->
            val exists = Files.exists(report)
            exists
        }
        val complexityReports = config.complexityReports.filter { report ->
            val exists = Files.exists(report)
            exists
        }

        Files.createDirectories(config.outputDir)

        val timings = mutableListOf<StageTiming>()
        val diagnostics = mutableListOf<String>()
        (config.coverageReports - coverageReports.toSet()).forEach {
            diagnostics += "Coverage report missing, using default coverage for unmatched files: $it"
        }
        (config.complexityReports - complexityReports.toSet()).forEach {
            diagnostics += "Complexity report missing, using default complexity for unmatched files: $it"
        }

        val projectBuilt = timed("inventory", timings) {
            sourceInventoryBuilder.buildInitialProject(
                projectName = config.projectName,
                projectVersion = config.projectVersion,
                basePath = config.basePath,
                sourceRoots = config.sourceRoots.map { it.toAbsolutePath().normalize() }
            )
        }

        val churnMerged = timed("ingest-churn", timings) {
            churnImporter.merge(
                project = projectBuilt,
                options = ChurnOptions(
                    days = config.churnDays,
                    churnLog = config.churnLog,
                    writeGitLogTo = if (config.churnLog != null) null else config.outputDir.resolve("gitlog.txt")
                ),
                repoRoot = config.basePath
            )
        }

        val complexityMerged = timed("ingest-complexity", timings) {
            if (complexityReports.isEmpty()) {
                churnMerged
            } else {
                val complexity = complexityReports
                    .flatMap { complexityImporter.parse(it) }
                    .map {
                        io.github.priospot.ingest.complexity.FileComplexity(
                            path = normalizeInputPath(it.path, config.basePath),
                            ncss = it.ncss,
                            maxCcn = it.maxCcn
                        )
                    }
                    .groupBy { it.path }
                    .map { (path, values) ->
                        io.github.priospot.ingest.complexity.FileComplexity(
                            path = path,
                            ncss = values.sumOf { it.ncss },
                            maxCcn = values.maxOf { it.maxCcn }
                        )
                    }
                complexityImporter.merge(churnMerged, complexity)
            }
        }

        val coverageDoc = timed("ingest-coverage-normalize", timings) {
            val merged = if (coverageReports.isEmpty()) {
                emptyList()
            } else {
                coverageReports
                    .map { coverageImporter.normalizeCoverageReport(it) }
                    .flatMap { it.files }
                    .groupBy { it.path }
                    .map { (path, files) ->
                        val lineCovered = files.sumOf { it.lineCoverage.covered }
                        val lineTotal = files.sumOf { it.lineCoverage.total }
                        val branchPairs = files.mapNotNull { it.branchCoverage }
                        val branchCoverage = if (branchPairs.isEmpty()) {
                            null
                        } else {
                            io.github.priospot.model.CoverageCounter(
                                covered = branchPairs.sumOf { it.covered },
                                total = branchPairs.sumOf { it.total }
                            )
                        }
                        io.github.priospot.model.CoverageFile(
                            path = path,
                            lineCoverage = io.github.priospot.model.CoverageCounter(lineCovered, lineTotal),
                            branchCoverage = branchCoverage
                        )
                    }
            }

            io.github.priospot.model.CoverageDocument(
                schemaVersion = 1,
                generator = "coverageReport",
                generatedAt = config.deterministicTimestamp ?: nowIsoTimestamp(),
                files = merged
            ).sortedDeterministic()
        }
        timed("write-coverage-json", timings) {
            coverageImporter.writeNormalizedJson(coverageDoc, config.outputDir.resolve("coverage.json"))
        }

        val coverageMerged = timed("ingest-coverage-merge", timings) {
            coverageImporter.merge(complexityMerged, coverageDoc)
        }
        val filesWithComplexityFromReport = coverageMerged.files.count { file ->
            file.metrics.any { it.name == MetricNames.MAX_CCN }
        }
        val defaultingResult = timed("apply-defaults", timings) {
            applyMetricDefaults(
                coverageMerged,
                defaultCoverageNumerator = config.defaultCoverageNumerator,
                defaultCoverageDenominator = config.defaultCoverageDenominator,
                defaultMaxCcn = config.defaultMaxCcn,
                basePath = config.basePath
            )
        }
        val withDefaults = defaultingResult.project
        if (defaultingResult.stats.sourceDerivedComplexityApplied > 0) {
            diagnostics += "Source-derived complexity applied to ${defaultingResult.stats.sourceDerivedComplexityApplied} files"
        }
        if (defaultingResult.stats.sourceDerivedNcssApplied > 0) {
            diagnostics += "Source-derived NCSS applied to ${defaultingResult.stats.sourceDerivedNcssApplied} files"
        }
        if (defaultingResult.stats.defaultComplexityApplied > 0) {
            diagnostics += "Complexity fallback (MAX-CCN=${config.defaultMaxCcn}) applied to ${defaultingResult.stats.defaultComplexityApplied} files"
        }

        val c3Result = timed("compute-c3", timings) {
            c3Computer.compute(withDefaults, config.churnDays)
        }
        diagnostics += c3Result.warnings

        val deterministicProject = c3Result.project.sortedDeep()
        val document = PanopticodeDocument(
            schemaVersion = 1,
            generatedAt = config.deterministicTimestamp ?: nowIsoTimestamp(),
            project = deterministicProject
        )

        val panopticodeJson = config.outputDir.resolve("panopticode.json")
        timed("write-panopticode-json", timings) {
            Files.writeString(panopticodeJson, ModelJson.mapper.writeValueAsString(document))
        }

        if (config.emitCompatibilityXml) {
            timed("write-compat-xml", timings) {
                compatXmlExporter.write(document, config.outputDir.resolve("panopticode.xml"))
            }
        }

        val reportPaths = linkedMapOf<ReportType, Path>()
        timed("generate-svg-reports", timings) {
            reportPaths[ReportType.PRIOSPOT] = config.outputDir.resolve("priospot-interactive-treemap.svg")
            reportPaths[ReportType.COVERAGE] = config.outputDir.resolve("coverage-interactive-treemap.svg")
            reportPaths[ReportType.COMPLEXITY] = config.outputDir.resolve("complexity-interactive-treemap.svg")
            reportPaths[ReportType.CHURN] = config.outputDir.resolve("churn-interactive-treemap.svg")
            reportPaths.forEach { (type, path) ->
                svgTreemapReporter.generateInteractiveTreemap(deterministicProject, type, path)
            }
        }

        val summary = mapOf(
            "filesParsed" to deterministicProject.files.size,
            "filesWithChurn" to deterministicProject.files.count { file -> file.metrics.any { it.name == "Times Changed" } },
            "filesWithCoverage" to deterministicProject.files.count { file -> file.metrics.any { it.name == "Line Coverage" } },
            "filesWithComplexity" to deterministicProject.files.count { file -> file.metrics.any { it.name == "MAX-CCN" } },
            "filesWithC3Computed" to c3Result.filesComputed,
            "filesWithComplexityFromReport" to filesWithComplexityFromReport,
            "filesWithFallbackComplexity" to defaultingResult.stats.defaultComplexityApplied,
            "filesWithComplexityFromSource" to defaultingResult.stats.sourceDerivedComplexityApplied
        )

        return PriospotResult(
            panopticodeJson = panopticodeJson,
            reportPaths = reportPaths,
            stageTimings = timings,
            diagnostics = diagnostics,
            summary = summary
        )
    }

    private fun <T> timed(stage: String, timings: MutableList<StageTiming>, block: () -> T): T {
        var result: Any? = null
        val millis = measureTimeMillis {
            result = block()
        }
        timings += StageTiming(stage, millis)
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun applyMetricDefaults(
        project: io.github.priospot.model.Project,
        defaultCoverageNumerator: Double,
        defaultCoverageDenominator: Double,
        defaultMaxCcn: Int,
        basePath: Path
    ): DefaultingResult {
        var sourceDerivedComplexityApplied = 0
        var sourceDerivedNcssApplied = 0
        var defaultComplexityApplied = 0
        val updatedFiles = project.files.map { file ->
            val byName = file.metrics.associateBy { it.name }.toMutableMap()
            if (MetricNames.LINE_COVERAGE !in byName) {
                val (coverageNumerator, coverageDenominator) = if (isTestSourceFile(file.path)) {
                    1.0 to 1.0
                } else {
                    defaultCoverageNumerator to defaultCoverageDenominator
                }
                byName[MetricNames.LINE_COVERAGE] = RatioMetric(
                    MetricNames.LINE_COVERAGE,
                    coverageNumerator,
                    coverageDenominator
                )
            }
            val sourceComplexity = loadSourceComplexity(file.path, basePath)
            if (MetricNames.NCSS !in byName && sourceComplexity != null) {
                byName[MetricNames.NCSS] = IntegerMetric(MetricNames.NCSS, sourceComplexity.ncss)
                sourceDerivedNcssApplied++
            }
            if (MetricNames.MAX_CCN !in byName) {
                val computedMaxCcn = sourceComplexity?.maxCcn
                if (computedMaxCcn != null) {
                    byName[MetricNames.MAX_CCN] = IntegerMetric(MetricNames.MAX_CCN, computedMaxCcn)
                    sourceDerivedComplexityApplied++
                } else {
                    byName[MetricNames.MAX_CCN] = IntegerMetric(MetricNames.MAX_CCN, defaultMaxCcn)
                    defaultComplexityApplied++
                }
            }
            if (MetricNames.LINES_ADDED !in byName) {
                byName[MetricNames.LINES_ADDED] = IntegerMetric(MetricNames.LINES_ADDED, 0)
            }
            if (MetricNames.LINES_REMOVED !in byName) {
                byName[MetricNames.LINES_REMOVED] = IntegerMetric(MetricNames.LINES_REMOVED, 0)
            }
            if (MetricNames.TIMES_CHANGED !in byName) {
                byName[MetricNames.TIMES_CHANGED] = IntegerMetric(MetricNames.TIMES_CHANGED, 0)
            }
            if (MetricNames.LINES_CHANGED_INDICATOR !in byName) {
                byName[MetricNames.LINES_CHANGED_INDICATOR] = DecimalMetric(MetricNames.LINES_CHANGED_INDICATOR, 0.0)
            }
            if (MetricNames.CHANGE_FREQUENCY_INDICATOR !in byName) {
                byName[MetricNames.CHANGE_FREQUENCY_INDICATOR] = DecimalMetric(MetricNames.CHANGE_FREQUENCY_INDICATOR, 0.0)
            }
            file.copy(metrics = byName.values.sortedBy { it.name })
        }
        return DefaultingResult(
            project = project.copy(files = updatedFiles),
            stats = DefaultingStats(
                sourceDerivedComplexityApplied = sourceDerivedComplexityApplied,
                sourceDerivedNcssApplied = sourceDerivedNcssApplied,
                defaultComplexityApplied = defaultComplexityApplied
            )
        )
    }

    private fun isTestSourceFile(path: String): Boolean {
        val normalized = normalizePath(path).lowercase()
        return normalized.startsWith("src/test/") || normalized.contains("/src/test/")
    }

    private fun normalizeInputPath(path: String, basePath: Path): String {
        val normalized = normalizePath(path)
        val baseNormalized = normalizePath(basePath.toAbsolutePath().normalize().toString())
        return if (normalized.startsWith(baseNormalized)) {
            normalizePath(basePath.toAbsolutePath().normalize().relativize(Path.of(normalized)).toString())
        } else {
            normalized
        }
    }

    private fun loadSourceComplexity(path: String, basePath: Path): io.github.priospot.ingest.complexity.KotlinFileComplexity? {
        if (!path.endsWith(".kt", ignoreCase = true)) return null
        val filePath = try {
            basePath.resolve(path).normalize()
        } catch (_: InvalidPathException) {
            return null
        }
        return kotlinSourceComplexityAnalyzer.analyze(filePath)
    }

    private data class DefaultingResult(
        val project: io.github.priospot.model.Project,
        val stats: DefaultingStats
    )

    private data class DefaultingStats(
        val sourceDerivedComplexityApplied: Int,
        val sourceDerivedNcssApplied: Int,
        val defaultComplexityApplied: Int
    )
}
