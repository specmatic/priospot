package io.github.priospot.cli

import java.util.concurrent.Callable
import picocli.AutoComplete
import picocli.CommandLine

@CommandLine.Command(
    name = "priospot",
    description = ["Analyze repository hotspots using churn, complexity, and coverage (C3)."],
    commandListHeading = "%nCommands:%n",
    subcommands = [AnalyzeCommand::class, ReportCommand::class, AutoComplete.GenerateCompletion::class],
    mixinStandardHelpOptions = true,
    scope = CommandLine.ScopeType.INHERIT,
    versionProvider = PriospotVersionProvider::class,
    exitCodeOnExecutionException = -1,
)
class PriospotCommand : Callable<Int> {
    override fun call(): Int = 0
}
