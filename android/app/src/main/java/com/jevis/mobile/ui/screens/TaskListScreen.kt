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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jevis.mobile.ui.JevisWarning
import com.jevis.mobile.ui.TaskUiState
import com.jevis.mobile.ui.appLabel
import com.jevis.mobile.ui.statusLabel
import com.jevis.mobile.ui.taskTitle

@Composable
fun TaskListScreen(
    state: TaskUiState,
    refresh: () -> Unit,
    create: () -> Unit,
    open: (String) -> Unit,
) {
    var filter by remember { mutableStateOf("全部") }
    val visible = state.tasks.filter { task ->
        when (filter) {
            "进行中" -> task.status in listOf("QUEUED", "PREPARING_DEVICE", "RUNNING", "VERIFYING")
            "待确认" -> task.status == "WAITING_CONFIRMATION"
            "已完成" -> task.status == "SUCCEEDED"
            "失败" -> task.status in listOf("FAILED", "NEEDS_REVIEW")
            else -> true
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        AppHeader("AI 任务") {
            Button(onClick = create, modifier = Modifier.align(Alignment.CenterEnd)) { Text("＋") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("同机并行执行中心", color = JevisMuted)
            Text("刷新", color = JevisBlue, modifier = Modifier.clickable(onClick = refresh))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("全部", "进行中", "待确认", "已完成", "失败").forEach { value ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value) })
            }
        }
        if (state.loading && state.tasks.isEmpty()) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.loading && visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.tasks.isEmpty()) "还没有任务，从“创建”开始" else "此分类暂无任务", color = JevisMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visible, key = { it.id }) { task -> TaskCard(task, open) }
            }
        }
    }
}

@Composable
private fun TaskCard(task: AgentTask, open: (String) -> Unit) {
    val running = task.status in listOf("QUEUED", "PREPARING_DEVICE", "RUNNING", "VERIFYING")
    val color = when (task.status) {
        "SUCCEEDED" -> JevisSuccess
        "FAILED", "NEEDS_REVIEW" -> JevisDanger
        "WAITING_CONFIRMATION" -> JevisWarning
        else -> JevisBlue
    }
    Card(
        Modifier.fillMaxWidth().clickable { open(task.id) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, JevisOutline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(MaterialTheme.shapes.extraLarge).background(color))
                Text(task.instruction.taskTitle(), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(task.status.statusLabel(), color = color, style = MaterialTheme.typography.labelMedium)
            }
            Text(task.events.lastOrNull()?.summary ?: "等待调度", color = JevisMuted)
            if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp), color = color)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.createdAt.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall, color = JevisMuted)
                Text(task.targetApp.appLabel(), style = MaterialTheme.typography.bodySmall, color = JevisMuted)
            }
        }
    }
}
