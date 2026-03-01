package io.github.priospot.ingest.source

import io.github.priospot.model.FileEntry
import io.github.priospot.model.Project
import io.github.priospot.model.normalizePath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class SourceInventoryBuilder {
    fun buildInitialProject(
        projectName: String,
        projectVersion: String?,
        basePath: Path,
        sourceRoots: List<Path>
    ): Project {
        val files = sourceRoots
            .flatMap { sourceRoot ->
                if (!Files.exists(sourceRoot)) {
                    emptyList()
                } else {
                    Files.walk(sourceRoot).use { pathStream ->
                        pathStream
                            .filter { it.isRegularFile() }
                            .map { path ->
                                val normalizedPath =
                                    runCatching { basePath.relativize(path).toString() }
                                        .getOrElse { path.toAbsolutePath().normalize().toString() }
                                FileEntry(name = path.name, path = normalizePath(normalizedPath))
                            }
                            .toList()
                    }
                }
            }
            .distinctBy { it.path }
            .sortedBy { it.path }

        return Project(
            name = projectName,
            version = projectVersion,
            basePath = normalizePath(basePath.toAbsolutePath().normalize().toString()),
            files = files
        )
    }
}
