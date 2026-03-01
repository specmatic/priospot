package io.github.priospot.ingest.coverage

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.priospot.model.CoverageCounter
import io.github.priospot.model.CoverageDocument
import io.github.priospot.model.CoverageFile
import io.github.priospot.model.Metric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.ModelJson
import io.github.priospot.model.Project
import io.github.priospot.model.RatioMetric
import io.github.priospot.model.normalizePath
import io.github.priospot.model.sortedDeterministic
import io.github.priospot.model.sortedMetrics
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class CoverageImporter {
    fun normalizeCoverageReport(reportPath: Path): CoverageDocument {
        val filename = reportPath.fileName.toString().lowercase()
        return when {
            filename.endsWith(".json") -> ModelJson.mapper.readValue<CoverageDocument>(Files.readString(reportPath)).sortedDeterministic()
            filename.endsWith(".xml") -> parseCoverageXml(reportPath).sortedDeterministic()
            else -> error("Unsupported coverage report format: $reportPath")
        }
    }

    fun merge(project: Project, coverageDocument: CoverageDocument): Project {
        val byPath = coverageDocument.files.associateBy { normalizePath(it.path) }
        val projectPaths = project.files.map { it.path }

        val merged = project.files.map { file ->
            val coverageFile = byPath[file.path]
                ?: resolveBySuffix(byPath, projectPaths, file.path)
                ?: return@map file
            val metrics = mutableListOf<Metric>()
            metrics += RatioMetric(
                MetricNames.LINE_COVERAGE,
                coverageFile.lineCoverage.covered.toDouble(),
                coverageFile.lineCoverage.total.toDouble()
            )
            coverageFile.branchCoverage?.let {
                metrics += RatioMetric(MetricNames.BRANCH_COVERAGE, it.covered.toDouble(), it.total.toDouble())
            }
            val map = file.metrics.associateBy { it.name }.toMutableMap()
            metrics.forEach { map[it.name] = it }
            file.copy(metrics = map.values.toList().sortedMetrics())
        }

        return project.copy(files = merged)
    }

    private fun resolveBySuffix(
        byPath: Map<String, CoverageFile>,
        projectPaths: List<String>,
        projectFilePath: String
    ): CoverageFile? {
        val candidates = byPath.entries.filter { (_, coverage) ->
            projectFilePath == coverage.path || projectFilePath.endsWith("/${coverage.path}")
        }
        if (candidates.size != 1) {
            return null
        }
        val coveragePath = candidates.single().key
        // Avoid accidental cross-module collisions where multiple project files share same suffix.
        val collisions = projectPaths.count { it == coveragePath || it.endsWith("/$coveragePath") }
        return if (collisions == 1) candidates.single().value else null
    }

    private fun parseCoverageXml(reportPath: Path): CoverageDocument {
        val factory = DocumentBuilderFactory.newInstance()
        // JaCoCo XML includes a DOCTYPE for report.dtd, which is not always present beside the XML.
        // Parse without loading external DTD/entities so reports are portable across environments.
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        val doc = builder.parse(reportPath.toFile())
        val root = doc.documentElement
        val generated = java.time.Instant.now().toString()

        val files = when (root.tagName.lowercase()) {
            "report" -> parseJacocoLike(root)
            "coverage" -> parseCoberturaLike(root)
            else -> emptyList()
        }

        return CoverageDocument(
            schemaVersion = 1,
            generator = "coverageReport",
            generatedAt = generated,
            files = files
        )
    }

    private fun parseJacocoLike(root: Element): List<CoverageFile> {
        val sourceFiles = root.getElementsByTagName("sourcefile")
        return (0 until sourceFiles.length).mapNotNull { idx ->
            val node = sourceFiles.item(idx) as? Element ?: return@mapNotNull null
            val name = node.getAttribute("name").ifBlank { return@mapNotNull null }
            val packageNode = node.parentNode as? Element
            val packageName = packageNode?.getAttribute("name")?.trim('/').orEmpty()
            val path = normalizePath(listOf(packageName, name).filter { it.isNotBlank() }.joinToString("/"))

            val lineCounter = childCounters(node).firstOrNull { it.type == "LINE" } ?: XmlCounter("LINE", 0, 0)
            val branchCounter = childCounters(node).firstOrNull { it.type == "BRANCH" }

            CoverageFile(path, lineCounter.asCoverageCounter(), branchCounter?.asCoverageCounter())
        }
    }

    private fun parseCoberturaLike(root: Element): List<CoverageFile> {
        val classes = root.getElementsByTagName("class")
        return (0 until classes.length).mapNotNull { idx ->
            val node = classes.item(idx) as? Element ?: return@mapNotNull null
            val filename = normalizePath(node.getAttribute("filename"))
            if (filename.isBlank()) return@mapNotNull null

            val lines = node.getElementsByTagName("line")
            var total = 0
            var covered = 0
            for (lineIdx in 0 until lines.length) {
                val line = lines.item(lineIdx) as? Element ?: continue
                total += 1
                if ((line.getAttribute("hits").toIntOrNull() ?: 0) > 0) {
                    covered += 1
                }
            }

            CoverageFile(
                path = filename,
                lineCoverage = CoverageCounter(covered = covered, total = total)
            )
        }
    }

    private data class XmlCounter(
        val type: String,
        val covered: Int,
        val missed: Int
    ) {
        fun asCoverageCounter(): CoverageCounter = CoverageCounter(covered = covered, total = covered + missed)
    }

    private fun childCounters(element: Element): List<XmlCounter> {
        val nodes = element.getElementsByTagName("counter")
        return (0 until nodes.length).mapNotNull { idx ->
            val node = nodes.item(idx) as? Element ?: return@mapNotNull null
            XmlCounter(
                type = node.getAttribute("type"),
                covered = node.getAttribute("covered").toIntOrNull() ?: 0,
                missed = node.getAttribute("missed").toIntOrNull() ?: 0
            )
        }
    }

    fun writeNormalizedJson(document: CoverageDocument, outputPath: Path) {
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, ModelJson.mapper.writeValueAsString(document.sortedDeterministic()))
    }
}
