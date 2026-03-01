package io.github.priospot.ingest.churn

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class ChurnImporterTest {
    private val importer = ChurnImporter()

    @Test
    fun `parses git numstat log and aggregates per file`() {
        val text =
            """
            --abc--2026-01-01--author
            3\t1\tsrc/main/kotlin/A.kt
            --def--2026-01-02--author
            2\t2\tsrc/main/kotlin/A.kt
            1\t0\tsrc/main/kotlin/B.kt
            """.trimIndent()

        val map = importer.parseGitNumstatLog(text)

        assertThat(map.getValue("src/main/kotlin/A.kt").linesAdded).isEqualTo(5)
        assertThat(map.getValue("src/main/kotlin/A.kt").linesRemoved).isEqualTo(3)
        assertThat(map.getValue("src/main/kotlin/A.kt").timesChanged).isEqualTo(2)
        assertThat(map.getValue("src/main/kotlin/B.kt").timesChanged).isEqualTo(1)
    }

    @Test
    fun `indicator functions are bounded`() {
        val freq = importer.changeFrequencyIndicator(numChanges = 5, days = 30)
        val lines = importer.linesChangedIndicator(linesChanged = 100, days = 30)

        assertThat(freq in 0.0..1.0).isTrue()
        assertThat(lines in 0.0..1.0).isTrue()
    }
}
