package com.jevis.mobile.executor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.os.Bundle
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AgentAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
        Log.i("OpenJevisAccessibility", "connected flags=${serviceInfo.flags}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    suspend fun showTaskResult(title: String, detail: String): Boolean = withContext(Dispatchers.Main) {
        val manager = getSystemService(WindowManager::class.java)
        // AccessibilityService's window manager carries its accessibility overlay token.
        // Refuse any unexpected display rather than showing a result inside the agent display.
        if (manager.defaultDisplay.displayId != 0) return@withContext false
        val density = resources.displayMetrics.density
        val view = TextView(this@AgentAccessibilityService).apply {
            text = "$title\n${detail.take(60)}"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding((18 * density).toInt(), (14 * density).toInt(),
                (18 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.rgb(32, 44, 65))
                cornerRadius = 16 * density
            }
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (64 * density).toInt()
            setTitle("open-jevis task result")
        }
        var added = false
        try {
            manager.addView(view, params)
            added = true
            Log.i("OpenJevisResult", "shown display=0 nonFocusable=true nonTouchable=true")
            delay(6_000)
            true
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("OpenJevisResult", "无法显示主屏任务提示", error)
            false
        } finally {
            if (added) manager.removeView(view)
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun snapshot(displayId: Int, preferredPackage: String? = null): UiSnapshot {
        requireIsolatedDisplay(displayId)
        val allWindows = windowsForDisplay(displayId)
        val preferredWindows = preferredPackage?.let { packageName ->
            allWindows.filter { it.root?.packageName?.toString() == packageName }
        }.orEmpty()
        val targetWindows = preferredWindows.ifEmpty { allWindows }
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

    suspend fun setText(displayId: Int, query: String, value: String): Boolean {
        requireIsolatedDisplay(displayId)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val node = findNode(displayId, query) ?: return false
        // Never use the global IME connection or force focus as an input fallback.
        // A matching package does not prove that an editor belongs to this display.
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
        val roots = windowsForDisplay(displayId).mapNotNull { it.root }
        val normalized = query.trim()
        return roots.firstNotNullOfOrNull { findRecursively(it, normalized, exact = true) }
            ?: roots.firstNotNullOfOrNull { findRecursively(it, normalized, exact = false) }
    }

    private fun findRecursively(
        node: AccessibilityNodeInfo,
        query: String,
        exact: Boolean,
    ): AccessibilityNodeInfo? {
        val candidates = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.viewIdResourceName,
        )
        val matches = if (exact) {
            candidates.any { it.equals(query, ignoreCase = true) }
        } else {
            candidates.any { it.contains(query, ignoreCase = true) }
        }
        if (matches) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findRecursively(child, query, exact)?.let { return it }
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

    private fun windowsForDisplay(displayId: Int): List<AccessibilityWindowInfo> {
        requireIsolatedDisplay(displayId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowsOnAllDisplays[displayId].orEmpty()
        } else {
            windows.filter { it.displayId == displayId }
        }
    }
}
