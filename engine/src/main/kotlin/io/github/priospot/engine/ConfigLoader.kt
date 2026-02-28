package io.github.priospot.engine

import java.nio.file.Path
import java.util.Properties

object ConfigLoader {
    fun fromPropertiesFile(path: Path, basePath: Path): PriospotConfig {
        val props = Properties().apply {
            path.toFile().inputStream().use { load(it) }
        }

        fun req(name: String): String = props.getProperty(name)
            ?: error("Missing required config key: $name")

        return PriospotConfig(
            projectName = req("projectName"),
            projectVersion = props.getProperty("projectVersion"),
            sourceRoots = req("sourceRoots").split(',').map { basePath.resolve(it.trim()) },
            coverageReports = req("coverageReports")
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { basePath.resolve(it) },
            complexityReports = req("complexityReports")
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { basePath.resolve(it) },
            churnDays = props.getProperty("churnDays")?.toInt() ?: 30,
            churnLog = props.getProperty("churnLog")?.takeIf { it.isNotBlank() }?.let { basePath.resolve(it) },
            outputDir = basePath.resolve(req("outputDir")),
            emitCompatibilityXml = props.getProperty("emitCompatibilityXml")?.toBoolean() ?: false,
            deterministicTimestamp = props.getProperty("deterministicTimestamp"),
            coverageTask = props.getProperty("coverageTask"),
            complexityTask = props.getProperty("complexityTask"),
            basePath = basePath
        )
    }
}
