package io.github.priospot.compute.c3

import io.github.priospot.model.DecimalMetric
import io.github.priospot.model.FileEntry
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.Metric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.Project
import io.github.priospot.model.RatioMetric
import io.github.priospot.model.sortedMetrics
import kotlin.math.exp

class C3Computer {
    fun compute(project: Project, churnDays: Int): C3Result {
        val warnings = mutableListOf<String>()
        var computedCount = 0

        val updated =
            project.files.map { file ->
                val metricMap = file.metrics.associateBy { it.name }

                val numChanges = (metricMap[MetricNames.TIMES_CHANGED] as? IntegerMetric)?.value
                val linesAdded = (metricMap[MetricNames.LINES_ADDED] as? IntegerMetric)?.value
                val linesRemoved = (metricMap[MetricNames.LINES_REMOVED] as? IntegerMetric)?.value
                val maxCcn = (metricMap[MetricNames.MAX_CCN] as? IntegerMetric)?.value
                val coverage = (metricMap[MetricNames.LINE_COVERAGE] as? RatioMetric)?.safeRatio()

                if (numChanges == null || linesAdded == null || linesRemoved == null || maxCcn == null || coverage == null) {
                    warnings += "Skipping C3 for ${file.path}: missing required metrics"
                    file
                } else {
                    val linesChanged = linesAdded + linesRemoved
                    val freq = changeFrequencyIndicator(numChanges, churnDays)
                    val lines = linesChangedIndicator(linesChanged, churnDays)
                    val ccn = maxCCNIndicator(maxCcn)
                    val c3 = ((((lines + freq) / 2.0) + ccn + (1 - coverage)) / 3.0)

                    computedCount += 1
                    file.withMetric(DecimalMetric(MetricNames.C3_INDICATOR, c3))
                }
            }

        return C3Result(project.copy(files = updated), warnings, computedCount)
    }

    fun changeFrequencyIndicator(numChanges: Int, days: Int): Double = 1 - exp((-2.3025 * numChanges) / days.toDouble())

    fun linesChangedIndicator(linesChanged: Int, days: Int): Double = 1 - exp((-0.05756 * linesChanged) / days.toDouble())

    fun maxCCNIndicator(maxCCN: Int): Double = 1 - exp(-0.092103 * maxCCN)

    private fun FileEntry.withMetric(metric: Metric): FileEntry {
        val map = metrics.associateBy { it.name }.toMutableMap()
        map[metric.name] = metric
        return copy(metrics = map.values.toList().sortedMetrics())
    }
}

data class C3Result(val project: Project, val warnings: List<String>, val filesComputed: Int)
