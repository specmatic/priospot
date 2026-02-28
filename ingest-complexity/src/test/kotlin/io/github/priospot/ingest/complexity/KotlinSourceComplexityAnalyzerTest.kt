package io.github.priospot.ingest.complexity

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KotlinSourceComplexityAnalyzerTest {
    @Test
    fun `computes ncss and ccn from kotlin source`() {
        val tempDir = createTempDirectory()
        val file = tempDir.resolve("Sample.kt")
        Files.writeString(
            file,
            """
            package demo

            class Sample {
                fun one(a: Int): Int {
                    if (a > 0 && a < 10) {
                        return a
                    }
                    return 0
                }

                fun two(items: List<Int>): Int =
                    when {
                        items.isEmpty() -> 0
                        else -> items.sum()
                    }
            }
            """.trimIndent()
        )

        val result = KotlinSourceComplexityAnalyzer().analyze(file)
        assertNotNull(result)
        assertEquals(14, result.ncss)
        assertEquals(3, result.maxCcn)
    }

    @Test
    fun `ignores comment and string tokens for complexity`() {
        val tempDir = createTempDirectory()
        val file = tempDir.resolve("Comments.kt")
        Files.writeString(
            file,
            """
            class Comments {
                // if && when should not count
                fun sample(): Int {
                    val text = "if && when"
                    return 1
                }
            }
            """.trimIndent()
        )

        val result = KotlinSourceComplexityAnalyzer().analyze(file)
        assertNotNull(result)
        assertEquals(1, result.maxCcn)
    }
}
