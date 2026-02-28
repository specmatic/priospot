package io.github.priospot.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CoverageDocument(
    val schemaVersion: Int = 1,
    val generator: String,
    val generatedAt: String,
    val files: List<CoverageFile> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CoverageFile(
    val path: String,
    val lineCoverage: CoverageCounter,
    val branchCoverage: CoverageCounter? = null,
    val classes: List<CoverageClass> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CoverageClass(
    val name: String,
    val lineCoverage: CoverageCounter? = null,
    val branchCoverage: CoverageCounter? = null,
    val methods: List<CoverageMethod> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CoverageMethod(
    val name: String,
    val signature: String? = null,
    val lineCoverage: CoverageCounter? = null,
    val branchCoverage: CoverageCounter? = null
)

data class CoverageCounter(
    val covered: Int,
    val total: Int
) {
    init {
        require(covered >= 0) { "covered must be >= 0" }
        require(total >= 0) { "total must be >= 0" }
        require(covered <= total) { "covered must be <= total" }
    }
}

fun CoverageDocument.sortedDeterministic(): CoverageDocument = copy(
    files = files.sortedBy { normalizePath(it.path) }.map { file ->
        file.copy(
            path = normalizePath(file.path),
            classes = file.classes.sortedBy { it.name }.map { klass ->
                klass.copy(
                    methods = klass.methods.sortedBy { it.signature ?: it.name }
                )
            }
        )
    }
)
