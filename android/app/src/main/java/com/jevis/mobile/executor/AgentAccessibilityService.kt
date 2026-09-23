package com.jevis.mobile.executor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred

class AgentAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun snapshot(displayId: Int): UiSnapshot {
        requireIsolatedDisplay(displayId)
        val targetWindows = windows.filter { it.displayId == displayId }
        val result = mutableListOf<UiNode>()
        targetWindows.forEach { window -> window.root?.let { collect(it, result) } }
        return UiSnapshot(
            displayId = displayId,
            packageName = targetWindows.firstNotNullOfOrNull {
                it.root?.packageName?.toString()
            },
            nodes = result.take(500),
        )
    }

    fun click(displayId: Int, query: String): Boolean {
        requireIsolatedDisplay(displayId)
        val node = findNode(displayId, query) ?: return false
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    fun setText(displayId: Int, query: String, value: String): Boolean {
        requireIsolatedDisplay(displayId)
        val node = findNode(displayId, query) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    suspend fun tap(displayId: Int, x: Float, y: Float): Boolean {
        requireIsolatedDisplay(displayId)
        val path = Path().apply { moveTo(x, y) }
        return dispatch(displayId, path, 1L, 80L)
    }

    suspend fun swipe(
        displayId: Int,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        durationMs: Long = 450L,
    ): Boolean {
        requireIsolatedDisplay(displayId)
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        return dispatch(displayId, path, 1L, durationMs)
    }

    private suspend fun dispatch(
        displayId: Int,
        path: Path,
        startMs: Long,
        durationMs: Long,
    ): Boolean {
        val result = CompletableDeferred<Boolean>()
        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, startMs, durationMs))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    result.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    result.complete(false)
                }
            },
            null,
        )
        return accepted && result.await()
    }

    private fun findNode(displayId: Int, query: String): AccessibilityNodeInfo? {
        return windows.asSequence()
            .filter { it.displayId == displayId }
            .mapNotNull { it.root }
            .mapNotNull { findRecursively(it, query.trim()) }
            .firstOrNull()
    }

    private fun findRecursively(
        node: AccessibilityNodeInfo,
        query: String,
    ): AccessibilityNodeInfo? {
        val matches = node.text?.toString()?.contains(query, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true ||
            node.viewIdResourceName?.contains(query, ignoreCase = true) == true
        if (matches) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findRecursively(child, query)?.let { return it }
        }
        return null
    }

    private fun collect(node: AccessibilityNodeInfo, output: MutableList<UiNode>) {
        val bounds = Rect().also(node::getBoundsInScreen)
        if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() ||
            !node.viewIdResourceName.isNullOrBlank() || node.isClickable || node.isEditable
        ) {
            output += UiNode(
                text = node.text?.toString().orEmpty(),
                description = node.contentDescription?.toString().orEmpty(),
                viewId = node.viewIdResourceName.orEmpty(),
                className = node.className?.toString().orEmpty(),
                clickable = node.isClickable,
                editable = node.isEditable,
                bounds = bounds.toShortString(),
            )
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collect(it, output) }
        }
    }

    private fun requireIsolatedDisplay(displayId: Int) {
        require(displayId > 0) { "拒绝操作物理主屏 Display 0" }
    }
}
