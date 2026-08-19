package io.github.androiddesktop

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Shell-UID companion started from wireless ADB via app_process.
 * It listens only on IPv4 loopback and requires a per-install random token before every command.
 */
object PrivilegedShellCore {
    @JvmStatic
    fun main(args: Array<String>) {
        val expectedToken = args.firstOrNull()?.trim().orEmpty()
        require(TOKEN_PATTERN.matches(expectedToken)) { "missing or invalid core auth token" }

        ServerSocket().use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress(InetAddress.getByName(PrivilegedCoreClient.LOOPBACK_HOST), PrivilegedCoreClient.PORT))
            println("ANDROIDDESKTOP_CORE_READY host=${PrivilegedCoreClient.LOOPBACK_HOST} port=${PrivilegedCoreClient.PORT}")
            while (true) {
                val socket = server.accept()
                runCatching {
                    socket.use { client ->
                        val reader = BufferedReader(InputStreamReader(client.inputStream, Charsets.UTF_8))
                        val writer = OutputStreamWriter(client.outputStream, Charsets.UTF_8)
                        val request = reader.readLine().orEmpty()
                        val delimiter = request.indexOf('|')
                        val suppliedToken = if (delimiter > 0) request.substring(0, delimiter) else ""
                        val command = if (delimiter > 0) request.substring(delimiter + 1) else ""
                        val response = if (constantTimeEquals(expectedToken, suppliedToken)) {
                            handle(command)
                        } else {
                            "ERR auth"
                        }
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

    private fun constantTimeEquals(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))

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
    private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{40,64}")
}
