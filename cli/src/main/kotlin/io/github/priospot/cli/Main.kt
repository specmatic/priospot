package io.github.priospot.cli

import picocli.CommandLine

fun main(args: Array<String>) {
    val commandLine = createCommandLine()
    if (args.isEmpty()) {
        commandLine.usage(System.out)
        return
    }
    commandLine.execute(*args)
}

private fun createCommandLine(): CommandLine {
    val commandLine = CommandLine(PriospotCommand())
    commandLine.executionExceptionHandler =
        CommandLine.IExecutionExceptionHandler { ex, _, _ ->
            throw ex
        }
    commandLine.parameterExceptionHandler =
        CommandLine.IParameterExceptionHandler { ex, _ ->
            throw ex
        }
    return commandLine
}
