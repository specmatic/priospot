package io.github.priospot.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ModelTest {
    @Test
    fun `coverage document sorting is deterministic`() {
        val doc =
            CoverageDocument(
                generator = "test",
                generatedAt = "2026-01-01T00:00:00Z",
                files =
                    listOf(
                        CoverageFile(path = "b/File.kt", lineCoverage = CoverageCounter(1, 2)),
                        CoverageFile(path = "a/File.kt", lineCoverage = CoverageCounter(1, 2))
                    )
            )

        val sorted = doc.sortedDeterministic()

        assertThat(sorted.files.first().path).isEqualTo("a/File.kt")
    }
}
