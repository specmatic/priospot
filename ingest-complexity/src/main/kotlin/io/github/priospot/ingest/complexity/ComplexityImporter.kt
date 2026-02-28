package io.github.priospot.ingest.complexity

import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.Metric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.ModelJson
import io.github.priospot.model.Project
import io.github.priospot.model.sortedMetrics
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.math.max

data class FileComplexity(
    val path: String,
    val ncss: Int,
    val maxCcn: Int
)

class ComplexityImporter {
    fun parse(reportPath: Path): List<FileComplexity> {
        val name = reportPath.fileName.toString().lowercase()
        return when {
            name.endsWith(".xml") -> parseDetektLikeXml(reportPath)
            name.endsWith(".json") -> parseJson(reportPath)
            else -> error("Unsupported complexity report format: $reportPath")
        }
    }

    fun merge(project: Project, complexity: List<FileComplexity>): Project {
        val byPath = complexity.associateBy { it.path }
        val projectPaths = project.files.map { it.path }
        val mergedFiles = project.files.map { file ->
            val c = byPath[file.path]
                ?: resolveBySuffix(byPath, projectPaths, file.path)
                ?: return@map file
            val update = listOf<Metric>(
                IntegerMetric(MetricNames.NCSS, c.ncss),
                IntegerMetric(MetricNames.MAX_CCN, c.maxCcn)
            )
            val map = file.metrics.associateBy { it.name }.toMutableMap()
            update.forEach { map[it.name] = it }
            file.copy(metrics = map.values.toList().sortedMetrics())
        }
        return project.copy(files = mergedFiles)
    }

    private fun resolveBySuffix(
        byPath: Map<String, FileComplexity>,
        projectPaths: List<String>,
        projectFilePath: String
    ): FileComplexity? {
        val candidates = byPath.entries.filter { (_, complexity) ->
            projectFilePath == complexity.path || projectFilePath.endsWith("/${complexity.path}")
        }
        if (candidates.size != 1) {
            return null
        }
        val complexityPath = candidates.single().key
        val collisions = projectPaths.count { it == complexityPath || it.endsWith("/$complexityPath") }
        return if (collisions == 1) candidates.single().value else null
    }

    private fun parseJson(reportPath: Path): List<FileComplexity> =
        ModelJson.mapper.readValue(Files.readString(reportPath))

    private fun parseDetektLikeXml(reportPath: Path): List<FileComplexity> {
        val dbf = DocumentBuilderFactory.newInstance()
        val builder = dbf.newDocumentBuilder()
        val doc = builder.parse(reportPath.toFile())
        val aggregate = linkedMapOf<String, MutableComplexity>()

        // Newer detekt XML is checkstyle-like: <file name="..."><error .../></file>
        val fileNodes = doc.getElementsByTagName("file")
        for (idx in 0 until fileNodes.length) {
            val fileNode = fileNodes.item(idx) as? Element ?: continue
            val filePath = fileNode.getAttribute("name").replace('\\', '/').trim()
            if (filePath.isBlank()) continue
            val current = aggregate.getOrPut(filePath) { MutableComplexity() }

            val errors = fileNode.getElementsByTagName("error")
            for (eIdx in 0 until errors.length) {
                val errorNode = errors.item(eIdx) as? Element ?: continue
                val source = errorNode.getAttribute("source")
                val message = errorNode.getAttribute("message")
                val line = errorNode.getAttribute("line").toIntOrNull() ?: 1
                current.maxObservedLine = max(current.maxObservedLine, line)

                when {
                    source.contains("CyclomaticComplexMethod", ignoreCase = true) -> {
                        val ccn = extractFirstInt(message, "complexity:\\s*(\\d+)") ?: 1
                        current.maxCcn = max(current.maxCcn, ccn)
                    }
                    source.contains("LongMethod", ignoreCase = true) -> {
                        val longMethod = extractFirstInt(message, "too long\\s*\\((\\d+)\\)") ?: 1
                        current.ncss += longMethod
                    }
                    source.contains("LargeClass", ignoreCase = true) ||
                        source.contains("TooManyFunctions", ignoreCase = true) ||
                        source.contains("ComplexCondition", ignoreCase = true) -> {
                        current.ncss += (extractFirstInt(message, "(\\d+)") ?: 1)
                    }
                }
            }
        }

        // Backward compatibility for older parser fixtures: <finding file=\"...\" id=\"...\" metric=\"...\"/>
        val findings = doc.getElementsByTagName("finding")
        for (idx in 0 until findings.length) {
            val node = findings.item(idx) as? Element ?: continue
            val file = node.getAttribute("file")
            if (file.isBlank()) continue

            val issue = node.getAttribute("id")
            val path = file.replace('\\', '/')
            val current = aggregate.getOrPut(path) { MutableComplexity() }
            when {
                issue.contains("CyclomaticComplexMethod", ignoreCase = true) -> {
                    val ccn = node.getAttribute("metric").toIntOrNull() ?: 1
                    current.maxCcn = max(current.maxCcn, ccn)
                }
                issue.contains("LongMethod", ignoreCase = true) ||
                    issue.contains("LargeClass", ignoreCase = true) ||
                    issue.contains("ComplexCondition", ignoreCase = true) -> {
                    current.ncss += node.getAttribute("metric").toIntOrNull() ?: 1
                }
            }
        }

        return aggregate.entries.map { (path, m) ->
            val inferredNcss = if (m.ncss > 0) m.ncss else m.maxObservedLine
            FileComplexity(path = path, ncss = inferredNcss.coerceAtLeast(1), maxCcn = m.maxCcn.coerceAtLeast(1))
        }
    }

    private class MutableComplexity(
        var ncss: Int = 0,
        var maxCcn: Int = 0,
        var maxObservedLine: Int = 1
    )

    private fun extractFirstInt(text: String, pattern: String): Int? {
        val regex = Regex(pattern)
        return regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
