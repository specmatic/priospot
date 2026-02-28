package io.github.priospot.ingest.churn

import io.github.priospot.model.DecimalMetric
import io.github.priospot.model.FileEntry
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.Metric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.Project
import io.github.priospot.model.normalizePath
import io.github.priospot.model.sortedMetrics
import kotlin.math.exp
import java.nio.file.Files
import java.nio.file.Path

data class ChurnOptions(
    val days: Int = 30,
    val churnLog: Path? = null,
    val writeGitLogTo: Path? = null
)

data class ChurnStats(
    val linesAdded: Int,
    val linesRemoved: Int,
    val timesChanged: Int
) {
    val linesChanged: Int get() = linesAdded + linesRemoved
}

class ChurnImporter {
    fun merge(project: Project, options: ChurnOptions, repoRoot: Path): Project {
        val perFile = loadPerFileChurn(options, repoRoot)
        val mergedFiles = project.files.map { file ->
            val stat = perFile[file.path] ?: return@map file
            file.withMetrics(
                listOf(
                    IntegerMetric(MetricNames.LINES_ADDED, stat.linesAdded),
                    IntegerMetric(MetricNames.LINES_REMOVED, stat.linesRemoved),
                    IntegerMetric(MetricNames.TIMES_CHANGED, stat.timesChanged),
                    DecimalMetric(MetricNames.LINES_CHANGED_INDICATOR, linesChangedIndicator(stat.linesChanged, options.days)),
                    DecimalMetric(MetricNames.CHANGE_FREQUENCY_INDICATOR, changeFrequencyIndicator(stat.timesChanged, options.days))
                )
            )
        }

        return project.copy(
            metrics = (project.metrics + IntegerMetric(MetricNames.CHURN_DURATION, options.days)).sortedMetrics(),
            files = mergedFiles
        )
    }

    private fun loadPerFileChurn(options: ChurnOptions, repoRoot: Path): Map<String, ChurnStats> {
        val logText = options.churnLog?.let {
            Files.readString(it)
        } ?: runGitLog(options.days, repoRoot)

        options.writeGitLogTo?.let {
            Files.createDirectories(it.parent)
            Files.writeString(it, logText)
        }

        return parseGitNumstatLog(logText)
    }

    private fun runGitLog(days: Int, repoRoot: Path): String {
        val command = listOf(
            "git",
            "log",
            "--all",
            "--numstat",
            "--date=short",
            "--pretty=format:--%h--%ad--%aN",
            "--no-renames",
            "--relative",
            "--since=$days.days"
        )
        val process = ProcessBuilder(command)
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        require(code == 0) { "git log command failed: $output" }
        return output
    }

    fun parseGitNumstatLog(text: String): Map<String, ChurnStats> {
        val result = linkedMapOf<String, ChurnStats>()
        text.lineSequence()
            .filter { line ->
                val trimmed = line.trim()
                trimmed.isNotBlank() && !trimmed.startsWith("--")
            }
            .forEach { line ->
                val trimmed = line.trim()
                val parts = trimmed.split('\t').let { parsed ->
                    if (parsed.size >= 3) parsed else trimmed.split("\\t")
                }
                if (parts.size < 3) return@forEach

                val added = parts[0].toIntOrNull() ?: 0
                val removed = parts[1].toIntOrNull() ?: 0
                val path = normalizePath(parts[2].trim())
                val previous = result[path]
                result[path] = previous?.copy(
                    linesAdded = previous.linesAdded + added,
                    linesRemoved = previous.linesRemoved + removed,
                    timesChanged = previous.timesChanged + 1
                )
                    ?: ChurnStats(added, removed, 1)
            }

        return result
    }

    fun changeFrequencyIndicator(numChanges: Int, days: Int): Double =
        1 - exp((-2.3025 * numChanges) / days.toDouble())

    fun linesChangedIndicator(linesChanged: Int, days: Int): Double =
        1 - exp((-0.05756 * linesChanged) / days.toDouble())

    private fun FileEntry.withMetrics(newMetrics: List<Metric>): FileEntry {
        val byName = metrics.associateBy { it.name }.toMutableMap()
        newMetrics.forEach { byName[it.name] = it }
        return copy(metrics = byName.values.toList().sortedMetrics())
    }
}
