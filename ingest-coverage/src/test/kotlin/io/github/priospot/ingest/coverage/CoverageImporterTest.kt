package io.github.priospot.ingest.coverage

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverageImporterTest {
    private val importer = CoverageImporter()

    @Test
    fun `normalizes jacoco xml`() {
        val temp = createTempDirectory()
        val xml = temp.resolve("jacoco.xml")
        Files.writeString(
            xml,
            """
            <report>
              <package name="com/example">
                <sourcefile name="Foo.kt">
                  <counter type="LINE" missed="20" covered="80"/>
                </sourcefile>
              </package>
            </report>
            """.trimIndent()
        )

        val doc = importer.normalizeCoverageReport(xml)

        assertEquals("com/example/Foo.kt", doc.files.single().path)
        assertEquals(80, doc.files.single().lineCoverage.covered)
        assertEquals(100, doc.files.single().lineCoverage.total)
    }
}
