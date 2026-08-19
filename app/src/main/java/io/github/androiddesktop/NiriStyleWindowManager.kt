package io.github.androiddesktop

import android.graphics.Rect
import kotlin.math.max

data class NiriColumnState(
    val windowId: Int,
    val app: DesktopApp,
    val bounds: Rect,
    val focused: Boolean,
    val floating: Boolean = false
)

data class NiriWorkspaceState(
    val columns: List<NiriColumnState>,
    val focusedIndex: Int,
    val scrollX: Int
) {
    val focusedColumn: NiriColumnState? get() = columns.getOrNull(focusedIndex)
}

class NiriStyleWindowManager(
    private val gapPx: Int,
    private val columnWidthPx: Int,
    private val columnHeightPx: Int
) {
    private val columns = mutableListOf<NiriColumnState>()
    private var focusedIndex = -1

    fun addWindow(id: Int, app: DesktopApp): NiriWorkspaceState {
        val index = columns.size
        val left = gapPx + index * (columnWidthPx + gapPx)
        val top = gapPx
        val column = NiriColumnState(
            windowId = id,
            app = app,
            bounds = Rect(left, top, left + columnWidthPx, top + columnHeightPx),
            focused = true,
            floating = false
        )
        columns.replaceAll { it.copy(focused = false) }
        columns += column
        focusedIndex = index
        return snapshot()
    }

    fun removeWindow(id: Int): NiriWorkspaceState {
        val removedIndex = columns.indexOfFirst { it.windowId == id }
        if (removedIndex >= 0) {
            columns.removeAt(removedIndex)
            focusedIndex = when {
                columns.isEmpty() -> -1
                focusedIndex >= columns.size -> columns.lastIndex
                focusedIndex > removedIndex -> max(0, focusedIndex - 1)
                else -> focusedIndex.coerceAtLeast(0)
            }
            columns.replaceAllIndexed { index, column -> column.copy(focused = index == focusedIndex) }
        }
        return snapshot()
    }

    fun focusNext(): NiriWorkspaceState {
        if (columns.isNotEmpty()) {
            focusedIndex = (focusedIndex + 1).coerceAtMost(columns.lastIndex)
            columns.replaceAllIndexed { index, column -> column.copy(focused = index == focusedIndex) }
        }
        return snapshot()
    }

    fun focusPrevious(): NiriWorkspaceState {
        if (columns.isNotEmpty()) {
            focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
            columns.replaceAllIndexed { index, column -> column.copy(focused = index == focusedIndex) }
        }
        return snapshot()
    }

    fun focusWindow(id: Int): NiriWorkspaceState {
        val index = columns.indexOfFirst { it.windowId == id }
        if (index >= 0) {
            focusedIndex = index
            columns.replaceAllIndexed { itemIndex, column -> column.copy(focused = itemIndex == focusedIndex) }
        }
        return snapshot()
    }

    fun toggleFloating(id: Int): NiriWorkspaceState {
        columns.replaceAllIndexed { index, column ->
            if (column.windowId == id) column.copy(floating = !column.floating, focused = true) else column.copy(focused = false)
        }
        focusedIndex = columns.indexOfFirst { it.windowId == id }
        return snapshot()
    }

    fun snapshot(): NiriWorkspaceState {
        val scroll = if (focusedIndex < 0) 0 else (focusedIndex * (columnWidthPx + gapPx)).coerceAtLeast(0)
        return NiriWorkspaceState(columns.toList(), focusedIndex, scroll)
    }

    fun describe(): String = buildString {
        appendLine("== niri-like window manager ==")
        appendLine("layout=scrollable horizontal tiling columns")
        appendLine("rule.newWindow=append-right; existing columns keep their width")
        appendLine("rule.focus=smooth-scroll-to-column + focus scale/glow")
        appendLine("rule.floating=utility layer; does not affect scroll strip")
        appendLine("columns=${columns.size}")
        appendLine("focusedIndex=$focusedIndex")
        columns.forEachIndexed { index, column ->
            appendLine("[$index] id=${column.windowId} app=${column.app.packageName.ifEmpty { column.app.label }} bounds=${column.bounds.flattenToString()} focused=${column.focused} floating=${column.floating}")
        }
    }
}

private inline fun <T> MutableList<T>.replaceAllIndexed(transform: (Int, T) -> T) {
    for (index in indices) this[index] = transform(index, this[index])
}
