package com.jevis.mobile.executor

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
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
            } catch (_: Exception) {
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
        var sequence = 3
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
        launchOnDisplay(task.targetApp, task.requiredDisplayId)
        delay(2_500)
        val snapshot = accessibility.snapshot(task.requiredDisplayId)
        event(
            "OBSERVATION",
            "已观察 ${snapshot.packageName}，发现 ${snapshot.nodes.size} 个可访问节点",
            payload = buildJsonObject {
                put("observation", Json.encodeToJsonElement(UiSnapshot.serializer(), snapshot))
            },
        )
        repeat(50) {
            val command = api.nextAction(deviceId, task.taskId)
            if (command == null) {
                delay(600)
                return@repeat
            }
            val success = executeAction(task, command, accessibility)
            delay(450)
            val after = accessibility.snapshot(task.requiredDisplayId)
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
            if (command.actionType == "FINISH") return
        }
    }

    private fun launchOnDisplay(packageName: String, displayId: Int) {
        require(displayId > 0)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: error("目标应用未安装：$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        startActivity(intent, options.toBundle())
    }

    private suspend fun executeAction(
        task: ExecutorTask,
        command: DeviceCommand,
        accessibility: AgentAccessibilityService,
    ): Boolean {
        val displayId = task.requiredDisplayId
        require(displayId > 0)
        val before = accessibility.snapshot(displayId)
        if (before.packageName != task.targetApp) return false
        fun string(name: String): String? =
            (command.arguments[name] as? JsonPrimitive)?.content
        fun float(name: String): Float? = string(name)?.toFloatOrNull()
        return when (command.actionType) {
            "CLICK" -> {
                val query = string("query") ?: return false
                if (query.contains("发送")) {
                    val visible = before.nodes.joinToString("\n") {
                        "${it.text} ${it.description} ${it.viewId}"
                    }
                    if (listOf(task.recipient, task.subject, task.body).any { it !in visible }) {
                        return false
                    }
                }
                accessibility.click(displayId, query)
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
        return manager.displays.map { it.displayId }.firstOrNull { it > 0 }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "隔离任务执行", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
