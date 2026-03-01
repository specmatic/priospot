package io.github.priospot.cli

import io.specmatic.priospot.cli.VersionInfo
import picocli.CommandLine

class PriospotVersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<out String?> = arrayOf("priospot ${VersionInfo.version}")
}
