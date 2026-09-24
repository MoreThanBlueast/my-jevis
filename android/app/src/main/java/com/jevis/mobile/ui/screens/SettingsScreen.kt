package com.jevis.mobile.ui.screens

import android.content.Intent
import android.provider.Settings
import android.hardware.display.DisplayManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jevis.mobile.executor.AgentAccessibilityService
import com.jevis.mobile.executor.ExecutorForegroundService

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accessibilityReady by produceState(AgentAccessibilityService.instance != null) {
        while (true) {
            value = AgentAccessibilityService.instance != null
            delay(1_000)
        }
    }
    val isolated = context.getSystemService(DisplayManager::class.java).displays
        .singleOrNull { it.displayId > 0 && it.name == "scrcpy" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("设备与沙盒", style = MaterialTheme.typography.headlineMedium)
        CapabilityCard("运行模式", "本机 USB 调试桥；显示隔离，不是应用数据沙盒")
        CapabilityCard("虚拟显示", isolated?.let { "已连接 Display ${it.displayId}；主屏不作为执行回退" }
            ?: "未连接隔离显示；请启动本机隔离显示脚本")
        CapabilityCard("定向输入", if (accessibilityReady) "执行器已连接" else "需要启用无障碍执行器")
        CapabilityCard("QQ 邮箱", "需预先登录；执行期间请勿在主屏打开同一应用")
        CapabilityCard("任务结果提示", "主屏显示 6 秒，不获取焦点、不接收触摸；需保持无障碍服务开启")
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("启用隔离执行器") }
        Button(
            onClick = {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ExecutorForegroundService::class.java),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("启动任务执行服务") }
        Button(
            enabled = accessibilityReady,
            onClick = {
                scope.launch {
                    AgentAccessibilityService.instance?.showTaskResult(
                        "任务已完成（提示测试）", "仅验证消息窗口，没有执行或发送邮件",
                    )
                    AgentAccessibilityService.instance?.showTaskResult(
                        "任务执行失败（提示测试）", "仅验证消息窗口，没有执行任何任务",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("测试主屏成功 / 失败提示") }
    }
}

@Composable
private fun CapabilityCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
