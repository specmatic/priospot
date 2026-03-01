package io.github.priospot.cli

import kotlin.system.exitProcess
import picocli.CommandLine

fun main(args: Array<String>) {
    runMain(args, ::exitProcess)
}

internal fun runMain(args: Array<String>, exit: (Int) -> Unit) {
    exit(runCli(args))
}

internal fun runCli(args: Array<String>): Int {
    val commandLine = CommandLine(PriospotCommand())
    if (args.isEmpty()) {
        commandLine.usage(System.out)
        return 0
    }
    return commandLine.execute(*args)
}
