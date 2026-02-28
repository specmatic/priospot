package io.github.priospot.report.svg

import io.github.priospot.model.DecimalMetric
import io.github.priospot.model.FileEntry
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.Metric
import io.github.priospot.model.MetricNames
import io.github.priospot.model.Project
import io.github.priospot.model.RatioMetric
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max

enum class ReportType {
    PRIOSPOT,
    COVERAGE,
    COMPLEXITY,
    CHURN
}

class SvgTreemapReporter {
    private data class Rect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double
    )

    private sealed interface TreeItem {
        val label: String
        val weight: Double
    }

    private data class PackageNode(
        override val label: String,
        val children: MutableMap<String, PackageNode> = linkedMapOf(),
        val files: MutableList<FileEntry> = mutableListOf()
    ) : TreeItem {
        override val weight: Double
            get() = files.sumOf { fileWeight(it) } + children.values.sumOf { it.weight }
    }

    private data class FileLeaf(
        val file: FileEntry
    ) : TreeItem {
        override val label: String get() = file.name
        override val weight: Double get() = fileWeight(file)
    }

    private data class LegendItem(
        val label: String,
        val fill: String,
        val stroke: String = "#111"
    )

    fun generateInteractiveTreemap(project: Project, type: ReportType, output: Path) {
        val width = 1600.0
        val height = 900.0
        val panelWidth = 420.0
        val treemapWidth = width - panelWidth

        val root = buildTree(project.files)
        val fileElements = mutableListOf<String>()
        val packageOverlayElements = mutableListOf<String>()
        val legendElements = buildLegendElements(type, treemapWidth, height)

        renderNode(
            node = root,
            rect = Rect(0.0, 0.0, treemapWidth, height),
            depth = 0,
            packageKey = "",
            reportType = type,
            packageOverlayElements = packageOverlayElements,
            fileElements = fileElements
        )

        val content = """
<svg xmlns="http://www.w3.org/2000/svg" width="${width.toInt()}" height="${height.toInt()}" viewBox="0 0 ${width.toInt()} ${height.toInt()}">
  <style>
    .treemap-cell { cursor: pointer; }
    .treemap-cell:hover { opacity: 0.88; }
    .package-label { font-family: monospace; font-size: 11px; fill: #333; }
  </style>
  <script><![CDATA[
    function clearChildren(node) {
      while (node && node.firstChild) {
        node.removeChild(node.firstChild);
      }
    }

    function setWrappedText(id, text, x, maxChars, maxLines, lineHeight) {
      var target = document.getElementById(id);
      if (!target) return;
      clearChildren(target);

      var value = (text && text.length) ? text : 'n/a';
      var lines = [];
      for (var i = 0; i < value.length; i += maxChars) {
        lines.push(value.substring(i, i + maxChars));
      }
      if (lines.length > maxLines) {
        lines = lines.slice(0, maxLines);
        var last = lines[maxLines - 1];
        lines[maxLines - 1] = (last.length > 3 ? last.substring(0, last.length - 3) : last) + '...';
      }

      for (var j = 0; j < lines.length; j++) {
        var tspan = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
        tspan.setAttribute('x', String(x));
        tspan.setAttribute('dy', j === 0 ? '0' : String(lineHeight));
        tspan.textContent = lines[j];
        target.appendChild(tspan);
      }
    }

    function showDetails(node) {
      var file = node.getAttribute('data-file') || 'n/a';
      var metric = node.getAttribute('data-primary') || 'n/a';
      var packageKey = node.getAttribute('data-package-key') || '';
      var selectedParts = packageKey.length ? packageKey.split('/') : [];
      var moduleKey = selectedParts.length ? selectedParts[0] : '';
      var metricsRaw = node.getAttribute('data-metrics') || '';
      var metrics = metricsRaw.length ? metricsRaw.split(';;') : [];

      setWrappedText('detail-file', file, 1210, 43, 4, 16);
      setWrappedText('detail-primary', metric, 1210, 43, 2, 16);

      var metricsText = document.getElementById('detail-metrics');
      clearChildren(metricsText);
      if (metricsText) {
        for (var i = 0; i < metrics.length; i++) {
          var tspan = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
          tspan.setAttribute('x', '1210');
          tspan.setAttribute('dy', i === 0 ? '0' : '18');
          tspan.textContent = metrics[i];
          metricsText.appendChild(tspan);
        }
      }

      var cells = document.getElementsByClassName('treemap-cell');
      for (var i = 0; i < cells.length; i++) {
        var stroke = cells[i].getAttribute('data-stroke');
        if (stroke) {
          cells[i].setAttribute('stroke', stroke);
        }
        cells[i].setAttribute('stroke-width', '1');
      }
      var packageBoxes = document.getElementsByClassName('package-box');
      for (var j = 0; j < packageBoxes.length; j++) {
        var packageStroke = packageBoxes[j].getAttribute('data-stroke');
        if (packageStroke) {
          packageBoxes[j].setAttribute('stroke', packageStroke);
        }
        packageBoxes[j].setAttribute('stroke-width', '1.4');
      }

      node.setAttribute('stroke', '#1a4dd9');
      node.setAttribute('stroke-width', '3');

      if (packageKey.length) {
        var ancestryPalette = ['#1a4dd9', '#3567e0', '#5888e8', '#7da7ef', '#a6c5f6', '#d0e0fb'];
        for (var k = 0; k < packageBoxes.length; k++) {
          var key = packageBoxes[k].getAttribute('data-package-key') || '';
          if (!key.length) continue;

          if (packageKey === key || packageKey.indexOf(key + '/') === 0) {
            var keyParts = key.split('/');
            var distance = selectedParts.length - keyParts.length;
            if (distance < 0) continue;

            var color = ancestryPalette[Math.min(distance, ancestryPalette.length - 1)];
            var width = distance === 0 ? '3.2' : '2.4';

            // Module-level box gets a distinct color to make module boundaries obvious.
            if (keyParts.length === 1 && keyParts[0] === moduleKey) {
              color = '#f57c00';
              width = '3.4';
            }

            packageBoxes[k].setAttribute('stroke', color);
            packageBoxes[k].setAttribute('stroke-width', width);
          }
        }
      }
    }
  ]]></script>

  <rect x="0" y="0" width="${treemapWidth.toInt()}" height="${height.toInt()}" fill="#f8f8f8"/>
  ${fileElements.joinToString("\n")}
  ${packageOverlayElements.joinToString("\n")}

  <rect x="${treemapWidth}" y="0" width="${panelWidth.toInt()}" height="${height.toInt()}" fill="#f0f0f0" stroke="#c0c0c0"/>
  <text x="1210" y="30" font-family="monospace" font-size="18" fill="#111">File Details</text>
  <text x="1210" y="58" font-family="monospace" font-size="13" fill="#555">Selected File</text>
  <text id="detail-file" x="1210" y="78" font-family="monospace" font-size="12" fill="#111">Click a file box</text>

  <text x="1210" y="160" font-family="monospace" font-size="13" fill="#555">Primary Metric</text>
  <text id="detail-primary" x="1210" y="180" font-family="monospace" font-size="12" fill="#111">n/a</text>

  <text x="1210" y="220" font-family="monospace" font-size="13" fill="#555">All Metrics</text>
  <text id="detail-metrics" x="1210" y="244" font-family="monospace" font-size="12" fill="#111"></text>
  ${legendElements.joinToString("\n")}
</svg>
        """.trimIndent()

        Files.createDirectories(output.parent)
        Files.writeString(output, content)
    }

    private fun buildTree(files: List<FileEntry>): PackageNode {
        val root = PackageNode(label = "root")
        files.forEach { file ->
            val packageSegments = packageSegmentsFor(file.path)
            var current = root
            packageSegments.forEach { segment ->
                current = current.children.getOrPut(segment) { PackageNode(segment) }
            }
            current.files += file
        }
        return root
    }

    private fun renderNode(
        node: PackageNode,
        rect: Rect,
        depth: Int,
        packageKey: String,
        reportType: ReportType,
        packageOverlayElements: MutableList<String>,
        fileElements: MutableList<String>
    ) {
        if (rect.width <= 0.0 || rect.height <= 0.0) {
            return
        }

        val insetRect = if (depth == 0) rect else inset(rect, packageInsetForDepth(depth))
        if (insetRect.width <= 0.0 || insetRect.height <= 0.0) {
            return
        }

        val headerHeight = 0.0
        val contentRect = if (headerHeight > 0 && rect.height > headerHeight + 2) {
            Rect(insetRect.x, insetRect.y + headerHeight, insetRect.width, insetRect.height - headerHeight)
        } else {
            insetRect
        }

        if (depth > 0) {
            val stroke = when (depth % 4) {
                0 -> "#888"
                1 -> "#777"
                2 -> "#666"
                else -> "#555"
            }
            val packageName = if (packageKey.isBlank()) node.label else packageKey
            packageOverlayElements += """
<g>
  <rect class="package-box" x="${insetRect.x}" y="${insetRect.y}" width="${insetRect.width}" height="${insetRect.height}" fill="none" stroke="$stroke" stroke-width="1.4" data-package-key="${escape(packageKey)}" data-stroke="$stroke" pointer-events="none">
    <title>${escape(packageName)}</title>
  </rect>
</g>
            """.trimIndent()
        }

        val items = mutableListOf<TreeItem>()
        items += node.children.values.sortedBy { it.label }
        items += node.files.sortedBy { it.path }.map { FileLeaf(it) }

        val subRects = partition(items, contentRect)
        items.zip(subRects).forEach { (item, subRect) ->
            when (item) {
                is PackageNode -> {
                    val childPackageKey = if (packageKey.isBlank()) item.label else "$packageKey/${item.label}"
                    renderNode(item, subRect, depth + 1, childPackageKey, reportType, packageOverlayElements, fileElements)
                }
                is FileLeaf -> fileElements += renderFile(item.file, subRect, packageKey, reportType)
            }
        }
    }

    private fun packageSegmentsFor(path: String): List<String> {
        val normalized = path.replace('\\', '/')
        val markers = listOf("/src/main/kotlin/", "/src/test/kotlin/", "/src/main/java/", "/src/test/java/")
        val marker = markers.firstOrNull { normalized.contains(it) }
        if (marker == null) {
            return normalized.split('/').dropLast(1).filter { it.isNotBlank() }
        }

        val idx = normalized.indexOf(marker)
        val modulePrefix = normalized.substring(0, idx).trim('/').split('/').filter { it.isNotBlank() }
        val packageTail = normalized.substring(idx + marker.length)
            .split('/')
            .dropLast(1)
            .filter { it.isNotBlank() }
        return modulePrefix + packageTail
    }

    private fun partition(items: List<TreeItem>, rect: Rect): List<Rect> {
        if (items.isEmpty()) return emptyList()
        if (items.size == 1) return listOf(rect)

        val out = mutableListOf<Rect>()
        partitionRecursive(items, rect, out)
        return out
    }

    private fun partitionRecursive(items: List<TreeItem>, rect: Rect, out: MutableList<Rect>) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            out += rect
            return
        }

        val total = items.sumOf { it.weight }.coerceAtLeast(1.0)
        val half = total / 2.0
        var acc = 0.0
        var split = 0
        while (split < items.lastIndex && acc < half) {
            acc += items[split].weight
            split++
        }

        val leftItems = items.subList(0, split)
        val rightItems = items.subList(split, items.size)
        val ratio = leftItems.sumOf { it.weight } / total

        if (rect.width >= rect.height) {
            val leftWidth = rect.width * ratio
            val leftRect = Rect(rect.x, rect.y, leftWidth, rect.height)
            val rightRect = Rect(rect.x + leftWidth, rect.y, rect.width - leftWidth, rect.height)
            partitionRecursive(leftItems, leftRect, out)
            partitionRecursive(rightItems, rightRect, out)
        } else {
            val topHeight = rect.height * ratio
            val topRect = Rect(rect.x, rect.y, rect.width, topHeight)
            val bottomRect = Rect(rect.x, rect.y + topHeight, rect.width, rect.height - topHeight)
            partitionRecursive(leftItems, topRect, out)
            partitionRecursive(rightItems, bottomRect, out)
        }
    }

    private fun renderFile(file: FileEntry, rect: Rect, packageKey: String, type: ReportType): String {
        val drawRect = inset(rect, FILE_BOX_INSET)
        if (drawRect.width <= 0.0 || drawRect.height <= 0.0) {
            return ""
        }

        val primaryMetric = metricForType(file, type)
        val primaryDisplay = metricToDisplayOrNa(primaryMetric)
        val allMetricsDisplay = file.metrics.sortedBy { it.name }.joinToString(";;") { metricToDisplay(it) }
        val (fill, stroke) = colorAndStrokeFor(file, type)
        val title = "${escape(file.path)} | ${escape(primaryDisplay)}"

        return """
<g>
  <rect class="treemap-cell" x="${drawRect.x}" y="${drawRect.y}" width="${drawRect.width}" height="${drawRect.height}" fill="$fill" stroke="$stroke" stroke-width="1" data-file="${escape(file.path)}" data-primary="${escape(primaryDisplay)}" data-metrics="${escape(allMetricsDisplay)}" data-package-key="${escape(packageKey)}" data-stroke="$stroke" onclick="showDetails(this)">
    <title>$title</title>
  </rect>
</g>
        """.trimIndent()
    }

    private fun metricForType(file: FileEntry, type: ReportType): Metric? = when (type) {
        ReportType.PRIOSPOT -> file.metrics.firstOrNull { it.name == MetricNames.C3_INDICATOR }
        ReportType.COVERAGE -> file.metrics.firstOrNull { it.name == MetricNames.LINE_COVERAGE }
        ReportType.COMPLEXITY -> file.metrics.firstOrNull { it.name == MetricNames.MAX_CCN }
        ReportType.CHURN -> file.metrics.firstOrNull { it.name == MetricNames.TIMES_CHANGED }
    }

    private fun metricToDisplay(metric: Metric): String = when (metric) {
        is DecimalMetric -> "${metric.name}: ${"%.4f".format(metric.value)}"
        is RatioMetric -> "${metric.name}: ${"%.2f".format(metric.safeRatio() * 100)}%"
        is IntegerMetric -> "${metric.name}: ${metric.value}"
    }

    private fun metricToDisplayOrNa(metric: Metric?): String = metric?.let { metricToDisplay(it) } ?: "metric: n/a"

    private fun colorAndStrokeFor(file: FileEntry, type: ReportType): Pair<String, String> = when (type) {
        ReportType.PRIOSPOT -> c3ColorAndStroke((file.metrics.firstOrNull { it.name == MetricNames.C3_INDICATOR } as? DecimalMetric)?.value)
        ReportType.COVERAGE -> coverageColor((file.metrics.firstOrNull { it.name == MetricNames.LINE_COVERAGE } as? RatioMetric)?.safeRatio()) to "#111"
        ReportType.COMPLEXITY -> genericScale((file.metrics.firstOrNull { it.name == MetricNames.MAX_CCN } as? IntegerMetric)?.value?.toDouble(), 30.0) to "#111"
        ReportType.CHURN -> genericScale((file.metrics.firstOrNull { it.name == MetricNames.TIMES_CHANGED } as? IntegerMetric)?.value?.toDouble(), 25.0) to "#111"
    }

    private fun c3ColorAndStroke(c3: Double?): Pair<String, String> {
        val value = c3 ?: return "#b0b0b0" to "#111"
        return when {
            value < 0.3 -> "#2e7d32" to "#111"
            value < 0.6 -> "#f9a825" to "#111"
            value < 0.9 -> "#c62828" to "#111"
            else -> "#000000" to "#ff0000"
        }
    }

    private fun coverageColor(coverage: Double?): String {
        val ratio = coverage ?: return "#b0b0b0"
        return when {
            ratio >= 0.8 -> "#2e7d32"
            ratio >= 0.5 -> "#f9a825"
            else -> "#c62828"
        }
    }

    private fun genericScale(value: Double?, maxValue: Double): String {
        val safe = (value ?: 0.0).coerceIn(0.0, maxValue)
        val ratio = safe / maxValue
        val red = 255
        val green = (220 - (ratio * 170)).toInt()
        val blue = (220 - (ratio * 170)).toInt()
        return "#%02x%02x%02x".format(red, green, blue)
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun buildLegendElements(type: ReportType, treemapWidth: Double, height: Double): List<String> {
        val items = legendItemsFor(type)
        val x = treemapWidth + 30
        val yStart = height - (items.size * 24) - 56
        val elements = mutableListOf<String>()
        elements += """<text x="$x" y="$yStart" font-family="monospace" font-size="13" fill="#555">Legend</text>"""
        items.forEachIndexed { index, item ->
            val y = yStart + 18 + (index * 24)
            elements += """<rect x="$x" y="$y" width="14" height="14" fill="${item.fill}" stroke="${item.stroke}" stroke-width="1"/>"""
            elements += """<text x="${x + 22}" y="${y + 11}" font-family="monospace" font-size="12" fill="#222">${escape(item.label)}</text>"""
        }
        return elements
    }

    private fun legendItemsFor(type: ReportType): List<LegendItem> = when (type) {
        ReportType.PRIOSPOT -> listOf(
            LegendItem("C3 < 0.30 (Good)", "#2e7d32"),
            LegendItem("0.30 <= C3 < 0.60 (Watch)", "#f9a825"),
            LegendItem("0.60 <= C3 < 0.90 (Risk)", "#c62828"),
            LegendItem("C3 >= 0.90 (Critical)", "#000000", "#ff0000"),
            LegendItem("Metric missing", "#b0b0b0")
        )
        ReportType.COVERAGE -> listOf(
            LegendItem("Coverage >= 80%", "#2e7d32"),
            LegendItem("50% <= Coverage < 80%", "#f9a825"),
            LegendItem("Coverage < 50%", "#c62828"),
            LegendItem("Metric missing", "#b0b0b0")
        )
        ReportType.COMPLEXITY -> listOf(
            LegendItem("Lower complexity", genericScale(0.0, 30.0)),
            LegendItem("Medium complexity", genericScale(15.0, 30.0)),
            LegendItem("Higher complexity", genericScale(30.0, 30.0))
        )
        ReportType.CHURN -> listOf(
            LegendItem("Lower churn", genericScale(0.0, 25.0)),
            LegendItem("Medium churn", genericScale(12.5, 25.0)),
            LegendItem("Higher churn", genericScale(25.0, 25.0))
        )
    }

    private fun inset(rect: Rect, amount: Double): Rect {
        if (amount <= 0.0) return rect
        val width = (rect.width - (amount * 2.0)).coerceAtLeast(0.0)
        val height = (rect.height - (amount * 2.0)).coerceAtLeast(0.0)
        return Rect(rect.x + amount, rect.y + amount, width, height)
    }

    private fun packageInsetForDepth(depth: Int): Double = when {
        depth <= 0 -> 0.0
        depth == 1 -> MODULE_BOX_INSET
        depth == 2 -> TOP_PACKAGE_BOX_INSET
        else -> (SUBPACKAGE_BASE_INSET - ((depth - 3) * SUBPACKAGE_STEP_DOWN)).coerceAtLeast(SUBPACKAGE_MIN_INSET)
    }

    private companion object {
        const val MODULE_BOX_INSET = 6.0
        const val TOP_PACKAGE_BOX_INSET = 4.0
        const val SUBPACKAGE_BASE_INSET = 2.4
        const val SUBPACKAGE_STEP_DOWN = 0.3
        const val SUBPACKAGE_MIN_INSET = 1.0
        const val FILE_BOX_INSET = 0.8

        fun fileWeight(file: FileEntry): Double {
            val ncss = (file.metrics.firstOrNull { it.name == MetricNames.NCSS } as? IntegerMetric)?.value
            return max(1, ncss ?: 1).toDouble()
        }
    }
}
