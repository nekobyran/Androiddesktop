package io.github.androiddesktop

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.SecureRandom

class CoreAuthTokenStore(private val context: Context) {
    @Synchronized
    fun ensureToken(): String {
        val file = tokenFile()
        if (file.isFile) {
            val existing = file.readText(Charsets.UTF_8).trim()
            if (TOKEN_PATTERN.matches(existing)) return existing
        }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        file.parentFile?.mkdirs()
        file.writeText(token, Charsets.UTF_8)
        return token
    }

    fun tokenFile(): File {
        val root = requireNotNull(context.getExternalFilesDir(null)) {
            "external files directory unavailable"
        }
        return File(root, TOKEN_FILE_NAME)
    }

    companion object {
        const val TOKEN_FILE_NAME = "androiddesktop-core.token"
        private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{40,64}")
    }
}
