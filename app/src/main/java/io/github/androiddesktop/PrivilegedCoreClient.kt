package io.github.androiddesktop

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class PrivilegedCoreClient {
    data class Result(val success: Boolean, val response: String)

    fun ping(): Result = request("ping")

    fun launch(displayId: Int, packageName: String): Result =
        request("launch|$displayId|${sanitizePackage(packageName)}")

    fun tap(displayId: Int, x: Int, y: Int): Result =
        request("tap|$displayId|${x.coerceAtLeast(0)}|${y.coerceAtLeast(0)}")

    fun key(displayId: Int, keyCode: Int): Result =
        request("key|$displayId|${keyCode.coerceAtLeast(0)}")

    private fun request(command: String): Result = runCatching {
                LocalSocket().use { socket ->
            socket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            socket.soTimeout = 3500
            val writer = OutputStreamWriter(socket.outputStream, Charsets.UTF_8)
            writer.write(command)
            writer.write("\n")
            writer.flush()
            val response = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8)).readLine().orEmpty()
            Result(response.startsWith("OK"), response.ifEmpty { "empty response" })
        }
    }.getOrElse { Result(false, "${it.javaClass.simpleName}: ${it.message}") }

    private fun sanitizePackage(value: String): String {
        val normalized = value.trim()
        require(PACKAGE_REGEX.matches(normalized)) { "invalid package: $value" }
        return normalized
    }

    companion object {
        const val SOCKET_NAME = "androiddesktop_privileged_core_v1"
        private val PACKAGE_REGEX = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    }
}
