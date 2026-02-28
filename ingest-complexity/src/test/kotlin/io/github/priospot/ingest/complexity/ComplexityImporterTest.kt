package io.github.priospot.ingest.complexity

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class ComplexityImporterTest {
    private val importer = ComplexityImporter()

    @Test
    fun `parses detekt-like xml`() {
        val temp = createTempDirectory()
        val xml = temp.resolve("detekt.xml")
        Files.writeString(
            xml,
            """
            <checkstyle>
              <file name="src/main/kotlin/com/example/Foo.kt">
                <error line="10" severity="warning" message="x" source="detekt.CyclomaticComplexMethod" />
              </file>
              <finding file="src/main/kotlin/com/example/Foo.kt" id="CyclomaticComplexMethod" metric="7"/>
              <finding file="src/main/kotlin/com/example/Foo.kt" id="LongMethod" metric="20"/>
            </checkstyle>
            """.trimIndent()
        )

        val parsed = importer.parse(xml)

        assertEquals(1, parsed.size)
        assertEquals(7, parsed.single().maxCcn)
        assertEquals(20, parsed.single().ncss)
    }

    @Test
    fun `parses checkstyle detekt xml with complexity in message`() {
        val temp = createTempDirectory()
        val xml = temp.resolve("detekt-checkstyle.xml")
        Files.writeString(
            xml,
            """
            <checkstyle version="4.3">
              <file name="src/main/kotlin/com/example/Foo.kt">
                <error line="29" severity="warning" message="The function x appears to be too complex based on Cyclomatic Complexity (complexity: 20)." source="detekt.CyclomaticComplexMethod" />
                <error line="80" severity="warning" message="The function x is too long (69)." source="detekt.LongMethod" />
              </file>
            </checkstyle>
            """.trimIndent()
        )

        val parsed = importer.parse(xml)

        assertEquals(1, parsed.size)
        assertEquals("src/main/kotlin/com/example/Foo.kt", parsed.single().path)
        assertEquals(20, parsed.single().maxCcn)
        assertEquals(69, parsed.single().ncss)
    }
}
