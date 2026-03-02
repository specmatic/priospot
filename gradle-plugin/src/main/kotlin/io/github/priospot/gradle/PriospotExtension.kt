package io.github.priospot.gradle

import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class PriospotExtension
    @Inject
    constructor(objects: ObjectFactory) {
        val projectName: Property<String> = objects.property(String::class.java)
        val projectVersion: Property<String> = objects.property(String::class.java)
        val baseNamespace: Property<String> = objects.property(String::class.java)
        val sourceRoots: ListProperty<String> = objects.listProperty(String::class.java)
        val coverageReports: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
        val complexityReports: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
        val churnDays: Property<Int> = objects.property(Int::class.java).convention(30)
        val churnLog: RegularFileProperty = objects.fileProperty()
        val outputDir: DirectoryProperty = objects.directoryProperty()
        val emitCompatibilityXml: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
        val deterministicTimestamp: Property<String> = objects.property(String::class.java)
        val coverageTask: Property<String> = objects.property(String::class.java)
        val complexityTask: Property<String> = objects.property(String::class.java)
    }
