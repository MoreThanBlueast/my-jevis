package com.jevis.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jevis.mobile.model.AgentTask
import com.jevis.mobile.ui.JevisBlue
import com.jevis.mobile.ui.JevisDanger
import com.jevis.mobile.ui.JevisMuted
import com.jevis.mobile.ui.JevisOutline
import com.jevis.mobile.ui.JevisSuccess
import com.jevis.mobile.ui.appLabel
import com.jevis.mobile.ui.statusLabel
import com.jevis.mobile.ui.taskTitle

@Composable
fun TaskDetailScreen(
    id: String,
    task: AgentTask?,
    load: (String) -> Unit,
    cancel: (String) -> Unit,
    resolveApproval: (String, String, Boolean) -> Unit,
    back: () -> Unit,
) {
    LaunchedEffect(id) { load(id) }
    if (task == null || task.id != id) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载…") }
        return
    }
    val active = task.status !in listOf("SUCCEEDED", "FAILED", "CANCELED", "EXPIRED")
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppHeader(task.instruction.taskTitle())
        Text("‹ 返回", color = JevisBlue, modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = back))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, JevisOutline),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(13.dp).clip(MaterialTheme.shapes.extraLarge).background(if (active) JevisBlue else JevisSuccess))
                    Text(task.status.statusLabel(), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
                }
                Text("当前步骤：${task.events.lastOrNull()?.summary ?: "等待调度"}")
                Text("隔离显示 · ${task.targetApp.appLabel()}", color = JevisMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, JevisOutline),
        ) { Text(task.instruction, Modifier.padding(16.dp)) }
        task.approvals.lastOrNull { it.status == "PENDING" }?.let { approval ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6)),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("需要你的确认", style = MaterialTheme.typography.titleMedium)
                    Text(approval.actionSummary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { resolveApproval(id, approval.id, false) }) {
                            Text("拒绝")
                        }
                        Button(onClick = { resolveApproval(id, approval.id, true) }) {
                            Text("批准并继续")
                        }
                    }
                }
            }
        }
        Text("执行时间线", style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(task.events, key = { _, event -> event.id }) { index, event ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(12.dp).clip(MaterialTheme.shapes.extraLarge)
                                .background(if (index == task.events.lastIndex && active) JevisBlue else JevisSuccess)
                        )
                        if (index != task.events.lastIndex) Box(Modifier.size(width = 2.dp, height = 34.dp).background(JevisOutline))
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(event.summary)
                        Text(event.createdAt.take(19).replace('T', ' '), color = JevisMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (active) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { cancel(id) },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, JevisDanger),
                ) { Text("停止任务", color = JevisDanger) }
                Button(onClick = {}, modifier = Modifier.weight(1f), enabled = false) { Text("查看实时画面") }
            }
        }
    }
}
