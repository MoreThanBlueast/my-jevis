package com.jevis.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jevis.mobile.model.AgentTask

@Composable
fun TaskDetailScreen(id: String, task: AgentTask?, load: (String) -> Unit, cancel: (String) -> Unit, back: () -> Unit) {
    LaunchedEffect(id) { load(id) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = back) { Text("返回") }
            task?.let { Text(it.status, color = MaterialTheme.colorScheme.primary) }
        }
        if (task == null || task.id != id) { Text("正在加载…"); return@Column }
        Text(task.subject, style = MaterialTheme.typography.headlineSmall)
        Text("收件人：${task.recipient}")
        Card(Modifier.fillMaxWidth()) { Text(task.body, Modifier.padding(16.dp)) }
        Text("执行时间线", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(task.events, key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) { Text("#${event.sequence} ${event.eventType}"); Text(event.summary) }
                }
            }
        }
        if (task.status !in listOf("SUCCEEDED", "FAILED", "CANCELED", "EXPIRED")) {
            Button(onClick = { cancel(id) }, Modifier.fillMaxWidth()) { Text("取消任务") }
        }
    }
}

