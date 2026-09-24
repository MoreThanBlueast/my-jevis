package com.jevis.mobile.executor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ExecutorForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "open_jevis_executor"
        const val NOTIFICATION_ID = 2201
        val ALLOWED_PACKAGES = setOf(
            "com.tencent.androidqqmail",
            "com.android.calendar",
            "com.tencent.mm",
            "com.android.browser",
            "com.miui.notes",
            "com.autonavi.minimap",
            "com.miui.gallery",
            "com.android.contacts",
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = DeviceGatewayApi()
    private var worker: Job? = null
    private val taskStates = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("open-jevis 执行器")
                .setContentText("等待非零虚拟显示与任务")
                .setOngoing(true)
                .build(),
        )
        worker = scope.launch { runLoop() }
        scope.launch { monitorResults() }
    }

    private suspend fun monitorResults() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val preferences = getSharedPreferences("task_result_notifications", MODE_PRIVATE)
        val labels = mapOf(
            "SUCCEEDED" to "任务已完成", "FAILED" to "任务执行失败",
            "NEEDS_REVIEW" to "任务已暂停，需检查", "CANCELED" to "任务已取消",
            "EXPIRED" to "任务已过期", "WAITING_CONFIRMATION" to "任务等待你的确认",
        )
        while (scope.isActive) {
            try {
                val updates = api.statuses(deviceId)
                taskStates.keys.retainAll(updates.map { it.taskId }.toSet())
                for (update in updates) {
                    taskStates[update.taskId] = update.status
                    val label = labels[update.status] ?: continue
                    val key = "${update.taskId}:${update.status}"
                    if (preferences.getBoolean(key, false)) continue
                    val service = AgentAccessibilityService.instance ?: continue
                    if (service.showTaskResult(label, update.title)) {
                        preferences.edit().putBoolean(key, true).apply()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("OpenJevisExecutor", "任务结果同步失败，将自动重试")
            }
            delay(2_000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val userId = Process.myUid() / 100_000
        while (scope.isActive) {
            try {
                val displayId = isolatedDisplayId()
                val accessibility = AgentAccessibilityService.instance
                api.register(
                    DeviceRegistration(
                        deviceId = deviceId,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        hyperOsVersion = Build.VERSION.INCREMENTAL,
                        displayId = displayId,
                        profileUserId = userId,
                        virtualDisplayReady = displayId != null,
                        directedInputReady = displayId != null && accessibility != null,
                    )
                )
                if (displayId != null && accessibility != null) {
                    api.claim(deviceId)?.let { execute(deviceId, userId, it, accessibility) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("OpenJevisExecutor", "执行循环失败，将在下一轮恢复", error)
                // The next iteration re-registers. Side effects are never retried here.
            }
            delay(2_000)
        }
    }

    private suspend fun execute(
        deviceId: String,
        userId: Int,
        task: ExecutorTask,
        accessibility: AgentAccessibilityService,
    ) {
        require(task.requiredDisplayId > 0)
        require(task.requiredProfileUserId == userId)
        require(task.targetApp in ALLOWED_PACKAGES)
        var sequence = task.nextEventSequence
        suspend fun event(
            type: String,
            summary: String,
            status: String? = null,
            payload: JsonObject = buildJsonObject {},
        ) {
            api.event(
                deviceId,
                task.taskId,
                ExecutorEvent(
                    sequence = sequence++,
                    eventType = type,
                    summary = summary,
                    displayId = task.requiredDisplayId,
                    profileUserId = userId,
                    status = status,
                    payload = payload,
                ),
            )
        }

        event("EXECUTOR_STARTED", "隔离执行器开始处理 ${task.targetApp} 任务", "RUNNING")
        val snapshot = try {
            // The bridge reuses an existing isolated foreground app without relaunching,
            // and also checks whether the owner is currently using it on the main screen.
            // Never fall back to an ordinary launcher intent on HyperOS.
            api.prepareDisplay(deviceId, task.taskId)
            var observed = accessibility.snapshot(task.requiredDisplayId, task.targetApp)
            var attempts = 0
            while (observed.packageName != task.targetApp && attempts++ < 12) {
                delay(500)
                observed = accessibility.snapshot(task.requiredDisplayId, task.targetApp)
            }
            check(observed.packageName == task.targetApp) {
                "目标应用未在隔离显示前台：${observed.packageName}"
            }
            observed
        } catch (error: Exception) {
            event(
                "EXECUTOR_FAILED",
                "无法在隔离显示启动目标应用：${error.message}",
                "FAILED",
            )
            return
        }
        event(
            "OBSERVATION",
            "已观察 ${snapshot.packageName}，发现 ${snapshot.nodes.size} 个可访问节点",
            payload = buildJsonObject {
                put("observation", Json.encodeToJsonElement(UiSnapshot.serializer(), snapshot))
            },
        )
        repeat(900) {
            val state = taskStates[task.taskId]
            if (state in setOf("SUCCEEDED", "FAILED", "NEEDS_REVIEW", "CANCELED", "EXPIRED")) return
            val command = api.nextAction(deviceId, task.taskId)
            if (command == null) {
                delay(600)
                return@repeat
            }
            val success = executeAction(task, command, accessibility)
            delay(450)
            val after = accessibility.snapshot(task.requiredDisplayId, task.targetApp)
            api.completeAction(
                deviceId,
                command.id,
                CommandResult(
                    displayId = task.requiredDisplayId,
                    profileUserId = userId,
                    success = success,
                    summary = if (success) "动作完成：${command.summary}" else "动作失败：${command.summary}",
                    observation = after,
                ),
            )
            if (!success || command.actionType == "FINISH") return
        }
    }

    private suspend fun executeAction(
        task: ExecutorTask,
        command: DeviceCommand,
        accessibility: AgentAccessibilityService,
    ): Boolean {
        val displayId = task.requiredDisplayId
        require(displayId > 0)
        var before = accessibility.snapshot(displayId, task.targetApp)
        var snapshotAttempts = 0
        while (before.packageName != task.targetApp && snapshotAttempts++ < 10) {
            delay(150)
            before = accessibility.snapshot(displayId, task.targetApp)
        }
        if (before.packageName != task.targetApp) return false
        fun string(name: String): String? =
            (command.arguments[name] as? JsonPrimitive)?.content
        fun float(name: String): Float? = string(name)?.toFloatOrNull()
        return when (command.actionType) {
            "CLICK" -> {
                val query = string("query") ?: return false
                if (query.contains("发送") || command.summary.contains("发送")) {
                    // Never send twice for the same logical task. App-side evidence of a
                    // sent mail is unreliable: the sent folder may lag behind, so a planner
                    // that re-checks later can wrongly conclude the first click failed.
                    try {
                        val alreadySent = getSharedPreferences("isolated_send_guard", MODE_PRIVATE)
                            .getBoolean(sendGuardKey(task), false)
                        if (alreadySent) {
                            Log.w("OpenJevisExecutor", "同一任务已发生过发送动作，拒绝第二次发送")
                            return false
                        }
                    } catch (error: Exception) {
                        Log.w("OpenJevisExecutor", "无法读取发送去重标记，按未发送处理")
                    }
                    val visible = before.nodes.joinToString("\n") {
                        "${it.text} ${it.description} ${it.viewId}"
                    }
                    if (listOf(task.recipient, task.subject, task.body).any { it !in visible }) {
                        return false
                    }
                    val clicked = accessibility.click(displayId, query)
                    if (clicked) {
                        getSharedPreferences("isolated_send_guard", MODE_PRIVATE)
                            .edit().putBoolean(sendGuardKey(task), true).apply()
                    }
                    clicked
                } else {
                    accessibility.click(displayId, query)
                }
            }
            "SET_TEXT" -> {
                val query = string("query")
                val value = string("value")
                query != null && value != null && accessibility.setText(displayId, query, value)
            }
            "TAP" -> {
                val x = float("x")
                val y = float("y")
                x != null && y != null && x in 0f..1080f && y in 0f..2400f &&
                    accessibility.tap(displayId, x, y)
            }
            "SWIPE" -> {
                val fromX = float("from_x")
                val fromY = float("from_y")
                val toX = float("to_x")
                val toY = float("to_y")
                if (listOf(fromX, fromY, toX, toY).any { it == null }) false else {
                    accessibility.swipe(displayId, fromX!!, fromY!!, toX!!, toY!!)
                }
            }
            "WAIT" -> {
                delay(string("milliseconds")?.toLongOrNull()?.coerceIn(100, 5_000) ?: 600)
                true
            }
            "OBSERVE", "FINISH" -> true
            else -> false
        }
    }

    private fun isolatedDisplayId(): Int? {
        val manager = getSystemService(DisplayManager::class.java)
        // `singleOrNull` guards against ambiguous ownership, but it also returns null when a
        // second scrcpy display lingers. The caller treats null as "not ready" and waits,
        // which is safe; it is never a reason to fall back to Display 0.
        return manager.displays.filter { it.displayId > 0 && it.name == "scrcpy" }
            .singleOrNull()?.displayId
    }

    private fun sendGuardKey(task: ExecutorTask): String = "sent:${task.idempotencyKey}"

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "隔离任务执行", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
