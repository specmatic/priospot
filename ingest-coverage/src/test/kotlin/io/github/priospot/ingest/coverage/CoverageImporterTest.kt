package io.github.priospot.ingest.coverage

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.nio.file.Files
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

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

        assertThat(doc.files.single().path).isEqualTo("com/example/Foo.kt")
        assertThat(doc.files.single().lineCoverage.covered).isEqualTo(80)
        assertThat(doc.files.single().lineCoverage.total).isEqualTo(100)
    }

    @Test
    fun `normalizes jacoco xml with doctype and without local dtd`() {
        val temp = createTempDirectory()
        val xml = temp.resolve("jacoco.xml")
        Files.writeString(
            xml,
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
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

        assertThat(doc.files.single().path).isEqualTo("com/example/Foo.kt")
        assertThat(doc.files.single().lineCoverage.covered).isEqualTo(80)
        assertThat(doc.files.single().lineCoverage.total).isEqualTo(100)
    }
}
