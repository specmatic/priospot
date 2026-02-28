package io.github.priospot.ingest.churn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChurnImporterTest {
    private val importer = ChurnImporter()

    @Test
    fun `parses git numstat log and aggregates per file`() {
        val text = """
            --abc--2026-01-01--author
            3\t1\tsrc/main/kotlin/A.kt
            --def--2026-01-02--author
            2\t2\tsrc/main/kotlin/A.kt
            1\t0\tsrc/main/kotlin/B.kt
        """.trimIndent()

        val map = importer.parseGitNumstatLog(text)

        assertEquals(5, map.getValue("src/main/kotlin/A.kt").linesAdded)
        assertEquals(3, map.getValue("src/main/kotlin/A.kt").linesRemoved)
        assertEquals(2, map.getValue("src/main/kotlin/A.kt").timesChanged)
        assertEquals(1, map.getValue("src/main/kotlin/B.kt").timesChanged)
    }

    @Test
    fun `indicator functions are bounded`() {
        val freq = importer.changeFrequencyIndicator(numChanges = 5, days = 30)
        val lines = importer.linesChangedIndicator(linesChanged = 100, days = 30)

        assertTrue(freq in 0.0..1.0)
        assertTrue(lines in 0.0..1.0)
    }
}
