package com.jevis.mobile.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jevis.mobile.ui.TaskUiState

@Composable
fun TaskListScreen(state: TaskUiState, refresh: () -> Unit, open: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("任务中心", style = MaterialTheme.typography.headlineMedium); Text("QQ 邮箱并行任务", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = refresh) { Text("刷新") }
        }
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.loading && state.tasks.isEmpty()) Text("还没有任务，请从“新建”开始。")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.tasks, key = { it.id }) { task ->
                Card(Modifier.fillMaxWidth().clickable { open(task.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(task.subject, style = MaterialTheme.typography.titleMedium)
                            Text(task.status, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("发送至 ${task.recipient}")
                        Text(task.events.lastOrNull()?.summary ?: "等待执行", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

