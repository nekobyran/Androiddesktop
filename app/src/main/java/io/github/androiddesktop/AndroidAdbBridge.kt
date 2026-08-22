package io.github.androiddesktop

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
import java.io.File
import java.net.InetAddress
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Device-local ADB pairing/connection bridge.
 *
 * Android 11+ exposes Wireless debugging through TLS pairing and mDNS. This bridge
 * talks to the same adbd on the current device; it never pretends that pairing was
 * completed when the platform service is unavailable. The user still has to enable
 * Wireless debugging and request "Pair device with pairing code" in system Settings.
 */
class AndroidAdbBridge private constructor(private val appContext: Context) {

    data class PairingEndpoint(val host: String, val port: Int)

    data class PairingResult(
        val paired: Boolean,
        val connected: Boolean,
        val endpoint: PairingEndpoint?,
        val message: String
    )

    data class BootstrapResult(
        val connected: Boolean,
        val coreStarted: Boolean,
        val message: String
    )

    @Volatile
    private var cachedPairingEndpoint: PairingEndpoint? = null

    fun discoverPairingEndpoint(timeoutMs: Long = 20_000L): PairingEndpoint? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val endpoint = AtomicReference<PairingEndpoint?>(null)
        val latch = CountDownLatch(1)
        val mdns = AdbMdns(appContext, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { address: InetAddress?, port: Int ->
            val host = address?.hostAddress?.takeIf { it.isNotBlank() }
                ?: runCatching { AndroidUtils.getHostIpAddress(appContext) }.getOrNull()
                ?: "127.0.0.1"
            if (port in 1..65535) {
                endpoint.set(PairingEndpoint(host, port))
                latch.countDown()
            }
        }
        return try {
            mdns.start()
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            endpoint.get()?.also { cachedPairingEndpoint = it }
        } finally {
            runCatching { mdns.stop() }
        }
    }

    fun pair(pairingCode: String, explicitPort: Int? = null): PairingResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return PairingResult(false, false, null, "无线调试内置配对需要 Android 11 或更高版本")
        }
        val code = pairingCode.trim()
        if (!PAIRING_CODE.matches(code)) {
            return PairingResult(false, false, null, "请输入系统无线调试页面显示的 6 位配对码")
        }
        val endpoint = when {
            explicitPort != null && explicitPort in 1..65535 -> {
                val host = cachedPairingEndpoint?.host
                    ?: runCatching { AndroidUtils.getHostIpAddress(appContext) }.getOrNull()
                    ?: "127.0.0.1"
                PairingEndpoint(host, explicitPort)
            }
            else -> cachedPairingEndpoint ?: discoverPairingEndpoint()
        } ?: return PairingResult(false, false, null, "没有发现 _adb-tls-pairing._tcp 服务；请保持“使用配对码配对设备”弹窗打开")

        return runCatching {
            val manager = AndroidAdbConnectionManager.getInstance(appContext)
            val paired = manager.pair(endpoint.host, endpoint.port, code)
            if (!paired) {
                PairingResult(false, false, endpoint, "adbd 拒绝了本次配对，请重新生成配对码后重试")
            } else {
                val connected = manager.isConnected || runCatching {
                    manager.autoConnect(appContext, 8_000L)
                }.getOrDefault(false) || manager.isConnected
                PairingResult(true, connected, endpoint, if (connected) "配对成功，已连接本机 adbd" else "配对成功，正在等待无线调试连接服务")
            }
        }.getOrElse { error ->
            PairingResult(false, false, endpoint, "配对失败：${error.javaClass.simpleName}${error.message?.let { ": $it" }.orEmpty()}")
        }
    }

    fun connect(timeoutMs: Long = 8_000L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            val manager = AndroidAdbConnectionManager.getInstance(appContext)
            manager.isConnected || manager.autoConnect(appContext, timeoutMs) || manager.isConnected
        }.getOrDefault(false)
    }

    fun bootstrapCore(hostPackage: String, token: String): BootstrapResult {
        val safePackage = hostPackage.takeIf { PACKAGE_PATTERN.matches(it) }
            ?: return BootstrapResult(false, false, "宿主包名无效")
        if (!TOKEN_PATTERN.matches(token)) {
            return BootstrapResult(false, false, "core token 无效")
        }
        if (!connect()) return BootstrapResult(false, false, "未连接本机 adbd，无法启动 shell core")

        return runCatching {
            // Grants are best-effort because OEM builds may reject individual appops/settings.
            runShell("appops set $safePackage SYSTEM_ALERT_WINDOW allow")
            runShell("appops set $safePackage GET_USAGE_STATS allow")
            runShell("pm grant $safePackage android.permission.WRITE_SECURE_SETTINGS")
            runShell("settings put global enable_freeform_support 1")
            runShell("settings put global force_resizable_activities 1")

            val apkPath = runShell("pm path $safePackage | head -n 1 | cut -d: -f2")
                .lineSequence().map { it.trim() }.firstOrNull { it.startsWith("/") }
                ?: return@runCatching BootstrapResult(true, false, "已连接 adbd，但未找到宿主 APK 路径")

            runShell("pkill -f io.github.androiddesktop.PrivilegedShellCore || true")
            runShell(
                "CLASSPATH=$apkPath nohup app_process /system/bin io.github.androiddesktop.PrivilegedShellCore $token " +
                    ">/data/local/tmp/androiddesktop-core.log 2>&1 </dev/null &"
            )
            Thread.sleep(350L)
            val ping = PrivilegedCoreClient(appContext).ping()
            BootstrapResult(true, ping.success, if (ping.success) "本机无线 ADB 已连接，shell core 已认证并运行" else "ADB 已连接，但 shell core 尚未就绪：${ping.response}")
        }.getOrElse { error ->
            BootstrapResult(true, false, "启动 shell core 失败：${error.javaClass.simpleName}${error.message?.let { ": $it" }.orEmpty()}")
        }
    }

    fun runShell(command: String): String {
        require(command.isNotBlank())
        val manager = AndroidAdbConnectionManager.getInstance(appContext)
        check(manager.isConnected) { "ADB is not connected" }
        val stream = manager.openStream("shell:$command")
        return stream.use {
            it.openInputStream().bufferedReader(Charsets.UTF_8).use { reader -> reader.readText().trim() }
        }
    }

    companion object {
        private val PAIRING_CODE = Regex("\\d{6}")
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{40,64}")

        @Volatile
        private var instance: AndroidAdbBridge? = null

        fun get(context: Context): AndroidAdbBridge = instance ?: synchronized(this) {
            instance ?: AndroidAdbBridge(context.applicationContext).also { instance = it }
        }
    }
}

/**
 * LibADB key/certificate owner. Keys live only in app-internal storage so a pairing
 * survives ordinary app restarts but is removed on uninstall.
 */
private class AndroidAdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val privateKeyFile = File(context.filesDir, "adb-client-private.pk8")
        val certificateFile = File(context.filesDir, "adb-client-cert.der")
        val loadedPrivateKey = readPrivateKey(privateKeyFile)
        val loadedCertificate = readCertificate(certificateFile)
        if (loadedPrivateKey != null && loadedCertificate != null) {
            privateKey = loadedPrivateKey
            certificate = loadedCertificate
        } else {
            val keyGenerator = KeyPairGenerator.getInstance("RSA")
            keyGenerator.initialize(2048, SecureRandom())
            val pair = keyGenerator.generateKeyPair()
            privateKey = pair.private
            certificate = createCertificate(pair.public, pair.private)
            privateKeyFile.writeBytes(privateKey.encoded)
            certificateFile.writeBytes(certificate.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String = "Androiddesktop"

    companion object {
        @Volatile
        private var instance: AndroidAdbConnectionManager? = null

        fun getInstance(context: Context): AndroidAdbConnectionManager = instance ?: synchronized(this) {
            instance ?: AndroidAdbConnectionManager(context.applicationContext).also { instance = it }
        }

        private fun readPrivateKey(file: File): PrivateKey? = runCatching {
            if (!file.isFile) return@runCatching null
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(file.readBytes()))
        }.getOrNull()

        private fun readCertificate(file: File): Certificate? = runCatching {
            if (!file.isFile) return@runCatching null
            file.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        }.getOrNull()

        private fun createCertificate(publicKey: PublicKey, privateKey: PrivateKey): Certificate {
            val algorithm = "SHA512withRSA"
            val now = Date()
            val expires = Date(now.time + TimeUnit.DAYS.toMillis(3650))
            val owner = X500Name("CN=Androiddesktop")
            val extensions = CertificateExtensions().apply {
                set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier))
                set("PrivateKeyUsage", PrivateKeyUsageExtension(now, expires))
            }
            val info = X509CertInfo().apply {
                set("version", CertificateVersion(2))
                set("serialNumber", CertificateSerialNumber(SecureRandom().nextInt().ushr(1)))
                set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
                set("subject", CertificateSubjectName(owner))
                set("key", CertificateX509Key(publicKey))
                set("validity", CertificateValidity(now, expires))
                set("issuer", CertificateIssuerName(owner))
                set("extensions", extensions)
            }
            return X509CertImpl(info).apply { sign(privateKey, algorithm) }
        }
    }
}
