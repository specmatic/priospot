package io.github.priospot.ingest.complexity

import java.nio.file.Files
import java.nio.file.Path

data class KotlinFileComplexity(
    val ncss: Int,
    val maxCcn: Int
)

class KotlinSourceComplexityAnalyzer {
    fun analyze(filePath: Path): KotlinFileComplexity? {
        if (!Files.exists(filePath)) return null
        if (!filePath.fileName.toString().endsWith(".kt", ignoreCase = true)) return null

        val source = Files.readString(filePath)
        val normalized = stripCommentsAndStrings(source)
        val ncss = computeNcss(normalized)
        val maxCcn = computeMaxCcn(normalized)
        return KotlinFileComplexity(ncss = ncss.coerceAtLeast(1), maxCcn = maxCcn.coerceAtLeast(1))
    }

    private fun computeNcss(source: String): Int =
        source
            .lineSequence()
            .map(String::trim)
            .count { line -> line.isNotEmpty() }

    private fun computeMaxCcn(source: String): Int {
        val functionToken = Regex("""\bfun\b""")
        val decisionToken = Regex("""\bif\b|\bfor\b|\bwhile\b|\bwhen\b|\bcatch\b|&&|\|\||\?:""")
        val functionStarts = functionToken.findAll(source).map { it.range.first }.toList()
        if (functionStarts.isEmpty()) return 1

        var maxCcn = 1
        for (index in functionStarts.indices) {
            val start = functionStarts[index]
            val endExclusive = functionStarts.getOrNull(index + 1) ?: source.length
            val slice = source.substring(start, endExclusive)
            val ccn = 1 + decisionToken.findAll(slice).count()
            if (ccn > maxCcn) {
                maxCcn = ccn
            }
        }
        return maxCcn
    }

    private fun stripCommentsAndStrings(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        var inLineComment = false
        var blockDepth = 0
        var inString = false
        var inChar = false
        var inTripleString = false

        while (i < input.length) {
            val c = input[i]
            val next = if (i + 1 < input.length) input[i + 1] else '\u0000'

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false
                    out.append('\n')
                } else {
                    out.append(' ')
                }
                i++
                continue
            }

            if (blockDepth > 0) {
                if (c == '/' && next == '*') {
                    blockDepth++
                    out.append("  ")
                    i += 2
                    continue
                }
                if (c == '*' && next == '/') {
                    blockDepth--
                    out.append("  ")
                    i += 2
                    continue
                }
                out.append(if (c == '\n') '\n' else ' ')
                i++
                continue
            }

            if (inTripleString) {
                if (c == '"' && next == '"' && i + 2 < input.length && input[i + 2] == '"') {
                    inTripleString = false
                    out.append("   ")
                    i += 3
                } else {
                    out.append(if (c == '\n') '\n' else ' ')
                    i++
                }
                continue
            }

            if (inString) {
                if (c == '\\' && i + 1 < input.length) {
                    out.append("  ")
                    i += 2
                    continue
                }
                if (c == '"') {
                    inString = false
                }
                out.append(' ')
                i++
                continue
            }

            if (inChar) {
                if (c == '\\' && i + 1 < input.length) {
                    out.append("  ")
                    i += 2
                    continue
                }
                if (c == '\'') {
                    inChar = false
                }
                out.append(' ')
                i++
                continue
            }

            if (c == '/' && next == '/') {
                inLineComment = true
                out.append("  ")
                i += 2
                continue
            }

            if (c == '/' && next == '*') {
                blockDepth = 1
                out.append("  ")
                i += 2
                continue
            }

            if (c == '"' && next == '"' && i + 2 < input.length && input[i + 2] == '"') {
                inTripleString = true
                out.append("   ")
                i += 3
                continue
            }

            if (c == '"') {
                inString = true
                out.append(' ')
                i++
                continue
            }

            if (c == '\'') {
                inChar = true
                out.append(' ')
                i++
                continue
            }

            out.append(c)
            i++
        }

        return out.toString()
    }
}
