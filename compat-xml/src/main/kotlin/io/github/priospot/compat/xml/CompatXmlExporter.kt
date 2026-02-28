package io.github.priospot.compat.xml

import io.github.priospot.model.DecimalMetric
import io.github.priospot.model.IntegerMetric
import io.github.priospot.model.PanopticodeDocument
import io.github.priospot.model.RatioMetric
import java.nio.file.Files
import java.nio.file.Path

class CompatXmlExporter {
    fun write(document: PanopticodeDocument, output: Path) {
        val xml = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<panopticode schemaVersion=\"${document.schemaVersion}\" generatedAt=\"${document.generatedAt}\">")
            appendLine("  <project name=\"${escape(document.project.name)}\" version=\"${escape(document.project.version ?: "")}\" basePath=\"${escape(document.project.basePath)}\">")
            document.project.files.forEach { file ->
                appendLine("    <file name=\"${escape(file.name)}\" path=\"${escape(file.path)}\">")
                file.metrics.forEach { metric ->
                    when (metric) {
                        is IntegerMetric -> appendLine("      <metric kind=\"integer\" name=\"${escape(metric.name)}\" value=\"${metric.value}\"/>")
                        is DecimalMetric -> appendLine("      <metric kind=\"decimal\" name=\"${escape(metric.name)}\" value=\"${metric.value}\"/>")
                        is RatioMetric -> appendLine("      <metric kind=\"ratio\" name=\"${escape(metric.name)}\" numerator=\"${metric.numerator}\" denominator=\"${metric.denominator}\"/>")
                    }
                }
                appendLine("    </file>")
            }
            appendLine("  </project>")
            appendLine("</panopticode>")
        }

        Files.createDirectories(output.parent)
        Files.writeString(output, xml)
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
