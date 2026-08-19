package io.github.androiddesktop

import android.net.LocalServerSocket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/**
 * Shell-UID companion started from wireless ADB via app_process.
 * It owns only narrow launch/input operations for displays created by Androiddesktop.
 */
object PrivilegedShellCore {
    @JvmStatic
    fun main(args: Array<String>) {
        LocalServerSocket(PrivilegedCoreClient.SOCKET_NAME).use { server ->
            println("ANDROIDDESKTOP_CORE_READY socket=${PrivilegedCoreClient.SOCKET_NAME}")
            while (true) {
                val socket = server.accept()
                runCatching {
                    socket.use { client ->
                        val reader = BufferedReader(InputStreamReader(client.inputStream, Charsets.UTF_8))
                        val writer = OutputStreamWriter(client.outputStream, Charsets.UTF_8)
                        val command = reader.readLine().orEmpty()
                        val response = handle(command)
                        writer.write(response.replace('\n', ' '))
                        writer.write("\n")
                        writer.flush()
                    }
                }.onFailure { error ->
                    System.err.println("core-client-error ${error.javaClass.simpleName}: ${error.message}")
                }
            }
        }
    }

    private fun handle(command: String): String {
        val parts = command.split('|')
        return when (parts.firstOrNull()) {
            "ping" -> "OK shellUid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()}"
            "launch" -> {
                if (parts.size != 3) return "ERR usage launch|displayId|package"
                val displayId = parts[1].toIntOrNull() ?: return "ERR invalid displayId"
                val pkg = validatedPackage(parts[2]) ?: return "ERR invalid package"
                exec(
                    listOf(
                        "/system/bin/am", "start",
                        "--display", displayId.toString(),
                        "--activity-brought-to-front",
                        "-a", "android.intent.action.MAIN",
                        "-c", "android.intent.category.LAUNCHER",
                        "-p", pkg
                    )
                )
            }
                        "tap" -> {
                if (parts.size != 4) return "ERR usage tap|displayId|x|y"
                val displayId = parts[1].toIntOrNull() ?: return "ERR invalid displayId"
                val x = parts[2].toIntOrNull() ?: return "ERR invalid x"
                val y = parts[3].toIntOrNull() ?: return "ERR invalid y"
                exec(listOf("/system/bin/input", "-d", displayId.toString(), "tap", x.toString(), y.toString()))
            }
            "key" -> {
                if (parts.size != 3) return "ERR usage key|displayId|keyCode"
                val displayId = parts[1].toIntOrNull() ?: return "ERR invalid displayId"
                val keyCode = parts[2].toIntOrNull() ?: return "ERR invalid keyCode"
                exec(listOf("/system/bin/input", "-d", displayId.toString(), "keyevent", keyCode.toString()))
            }
            else -> "ERR unknown command"
        }
    }

    private fun validatedPackage(value: String): String? =
        value.trim().takeIf { PACKAGE_REGEX.matches(it) }

    private fun exec(argv: List<String>): String = runCatching {
        val process = ProcessBuilder(argv).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        val completed = process.waitFor(8, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return@runCatching "ERR timeout ${argv.firstOrNull().orEmpty()}"
        }
        if (process.exitValue() == 0) {
            "OK ${output.take(320)}"
        } else {
            "ERR exit=${process.exitValue()} ${output.take(320)}"
        }
    }.getOrElse { "ERR ${it.javaClass.simpleName}: ${it.message}" }

    private val PACKAGE_REGEX = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
}
