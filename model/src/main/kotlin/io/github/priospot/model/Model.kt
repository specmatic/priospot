package io.github.priospot.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PanopticodeDocument(val schemaVersion: Int = 1, val generatedAt: String, val project: Project)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Project(
    val name: String,
    val version: String?,
    val basePath: String,
    val metrics: List<Metric> = emptyList(),
    val files: List<FileEntry> = emptyList(),
    val supplements: List<SupplementDeclaration> = emptyList(),
    val packages: List<PackageEntry> = emptyList()
)

data class SupplementDeclaration(val name: String, val description: String)

data class PackageEntry(val name: String, val metrics: List<Metric> = emptyList(), val files: List<FileEntry> = emptyList())

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FileEntry(
    val name: String,
    val path: String,
    val metrics: List<Metric> = emptyList(),
    val classes: List<ClassEntry> = emptyList()
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClassEntry(
    val name: String,
    val fullyQualifiedName: String,
    val position: Position,
    val flags: ClassFlags,
    val metrics: List<Metric> = emptyList(),
    val methods: List<MethodEntry> = emptyList()
)

data class MethodEntry(
    val name: String,
    val fullyQualifiedName: String,
    val position: Position,
    val isConstructor: Boolean,
    val isAbstract: Boolean,
    val arguments: List<ArgumentEntry> = emptyList(),
    val metrics: List<Metric> = emptyList()
)

data class ArgumentEntry(
    val name: String,
    val fullyQualifiedType: String,
    val simpleType: String,
    val isParameterizedType: Boolean,
    val isVarArg: Boolean
)

data class Position(val line: Int, val column: Int)

data class ClassFlags(val isAbstract: Boolean, val isInterface: Boolean, val isEnum: Boolean, val isStatic: Boolean)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = IntegerMetric::class, name = "integer"),
    JsonSubTypes.Type(value = DecimalMetric::class, name = "decimal"),
    JsonSubTypes.Type(value = RatioMetric::class, name = "ratio")
)
sealed interface Metric {
    val name: String
}

data class IntegerMetric(override val name: String, val value: Int) : Metric

data class DecimalMetric(override val name: String, val value: Double) : Metric

data class RatioMetric(override val name: String, val numerator: Double, val denominator: Double) : Metric {
    fun safeRatio(): Double = if (denominator <= 0.0) 0.0 else numerator / denominator
}

object MetricNames {
    const val LINES_ADDED = "Lines Added"
    const val LINES_REMOVED = "Lines Removed"
    const val TIMES_CHANGED = "Times Changed"
    const val LINES_CHANGED_INDICATOR = "Lines Changed Indicator"
    const val CHANGE_FREQUENCY_INDICATOR = "Change Frequency Indicator"
    const val CHURN_DURATION = "Churn Duration"
    const val NCSS = "NCSS"
    const val MAX_CCN = "MAX-CCN"
    const val LINE_COVERAGE = "Line Coverage"
    const val BRANCH_COVERAGE = "Branch Coverage"
    const val METHOD_COVERAGE = "Method Coverage"
    const val C3_INDICATOR = "C3 Indicator"
}

object ModelJson {
    val mapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
}

fun nowIsoTimestamp(): String = Instant.now().toString()

fun normalizePath(path: String): String = path.replace('\\', '/')

fun List<Metric>.sortedMetrics(): List<Metric> = sortedBy { it.name }

fun FileEntry.sortedDeep(): FileEntry = copy(
    metrics = metrics.sortedMetrics(),
    classes =
        classes.sortedBy { it.fullyQualifiedName }.map { classEntry ->
            classEntry.copy(
                metrics = classEntry.metrics.sortedMetrics(),
                methods =
                    classEntry.methods.sortedBy { it.fullyQualifiedName }.map { methodEntry ->
                        methodEntry.copy(metrics = methodEntry.metrics.sortedMetrics())
                    }
            )
        }
)

fun Project.sortedDeep(): Project = copy(
    metrics = metrics.sortedMetrics(),
    files = files.sortedBy { it.path }.map { it.sortedDeep() },
    packages = packages.sortedBy { it.name }
)
