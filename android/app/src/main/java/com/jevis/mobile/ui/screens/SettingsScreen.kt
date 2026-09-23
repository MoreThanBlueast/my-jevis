package com.jevis.mobile.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jevis.mobile.executor.AgentAccessibilityService
import com.jevis.mobile.executor.ExecutorForegroundService

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val accessibilityReady = AgentAccessibilityService.instance != null
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("设备与沙盒", style = MaterialTheme.typography.headlineMedium)
        CapabilityCard("工作资料", "待检测")
        CapabilityCard("可信虚拟显示", "待检测；主屏 Display 0 永不作为回退")
        CapabilityCard("定向输入", if (accessibilityReady) "执行器已连接" else "需要启用无障碍执行器")
        CapabilityCard("QQ 邮箱", "需要在 AI 工作资料中预先登录")
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
