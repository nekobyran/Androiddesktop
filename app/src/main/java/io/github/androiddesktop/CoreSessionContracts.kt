package io.github.androiddesktop

import android.graphics.Rect

/**
 * Contract models for the privileged desktop container core.
 *
 * These models describe the bridge that must exist before Androiddesktop can render a real
 * third-party app inside an in-app desktop window. They are intentionally dependency-free so the
 * host can compile without Shizuku/root libraries while keeping the implementation boundary clear.
 */
data class CoreSessionRequest(
    val targetPackage: String,
    val requestedBounds: Rect,
    val requestedDensityDpi: Int = 420,
    val requestedWidth: Int = requestedBounds.width().coerceAtLeast(1),
    val requestedHeight: Int = requestedBounds.height().coerceAtLeast(1),
    val transport: CoreTransport = CoreTransport.WirelessAdbShell
)

data class CoreSessionDescriptor(
    val sessionId: String,
    val displayId: Int?,
    val targetPackage: String,
    val surfaceState: SurfaceBridgeState,
    val inputState: InputBridgeState,
    val lifecycleState: WindowLifecycleState,
    val message: String
)

enum class CoreTransport {
    WirelessAdbShell,
    Shizuku,
    RootShell,
    SystemSignedService
}

enum class SurfaceBridgeState {
    Missing,
    SurfaceAllocated,
    VirtualDisplayRequested,
    DisplayBound,
    Rendering
}

enum class InputBridgeState {
    Missing,
    CoordinateMapperReady,
    ShellInputFallback,
    BinderInjectionReady
}

enum class WindowLifecycleState {
    Created,
    StartingTarget,
    Attached,
    Paused,
    Closing,
    Failed
}

data class CoreReadinessReport(
    val wirelessDebugConnected: Boolean,
    val privilegedTransportReady: Boolean,
    val displaySessionReady: Boolean,
    val inputBridgeReady: Boolean,
    val targetTaskAttached: Boolean
) {
    val readyForRealEmbedding: Boolean
        get() = wirelessDebugConnected && privilegedTransportReady && displaySessionReady && inputBridgeReady && targetTaskAttached

    fun toHumanReport(): String = buildString {
        appendLine("== Core readiness ==")
        appendLine("wirelessDebugConnected=$wirelessDebugConnected")
        appendLine("privilegedTransportReady=$privilegedTransportReady")
        appendLine("displaySessionReady=$displaySessionReady")
        appendLine("inputBridgeReady=$inputBridgeReady")
        appendLine("targetTaskAttached=$targetTaskAttached")
        appendLine("readyForRealEmbedding=$readyForRealEmbedding")
        if (!readyForRealEmbedding) {
            appendLine("当前仍只能显示容器占位 Surface；必须完成全部 true 才能标记为真实 App 画面嵌入。")
        }
    }
}
