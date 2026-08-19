package io.github.androiddesktop

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class PrivilegedCoreClient(context: Context) {
    data class Result(val success: Boolean, val response: String)

    private val authToken = CoreAuthTokenStore(context.applicationContext).ensureToken()

    fun ping(): Result = request("ping")

    fun launch(displayId: Int, packageName: String): Result =
        request("launch|$displayId|${sanitizePackage(packageName)}")

    fun tap(displayId: Int, x: Int, y: Int): Result =
        request("tap|$displayId|${x.coerceAtLeast(0)}|${y.coerceAtLeast(0)}")

    fun key(displayId: Int, keyCode: Int): Result =
        request("key|$displayId|${keyCode.coerceAtLeast(0)}")

    private fun request(command: String): Result = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LOOPBACK_HOST, PORT), 1500)
            socket.soTimeout = 3500
            val writer = OutputStreamWriter(socket.outputStream, Charsets.UTF_8)
            writer.write(authToken)
            writer.write('|'.code)
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
        const val LOOPBACK_HOST = "127.0.0.1"
        const val PORT = 38388
        private val PACKAGE_REGEX = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    }
}
