package com.jevis.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jevis.mobile.ui.JevisBlue
import com.jevis.mobile.ui.JevisMuted
import com.jevis.mobile.ui.JevisOutline

private data class AppOption(val value: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    loading: Boolean,
    error: String?,
    create: (String, String?, Boolean) -> Unit,
) {
    var instruction by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    val apps = listOf(
        AppOption("AUTO", "自动识别"),
        AppOption("com.tencent.androidqqmail", "QQ 邮箱"),
        AppOption("com.google.android.calendar", "日历"),
        AppOption("com.tencent.mm", "微信"),
        AppOption("com.android.browser", "浏览器"),
        AppOption("com.miui.notes", "便签"),
        AppOption("com.autonavi.minimap", "高德地图"),
        AppOption("com.miui.gallery", "相册"),
    )
    var selected by remember { mutableStateOf(apps.first()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AppHeader("新建任务")
        Text("告诉 AI 要完成什么", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = instruction,
            onValueChange = { if (it.length <= 1000) instruction = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 7,
            maxLines = 12,
            placeholder = { Text("例如：使用 QQ 邮箱发送问候邮件，或查询本周日程并整理摘要") },
            supportingText = { Text("${instruction.length} / 1000") },
        )

        Text("执行策略", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, JevisOutline),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("运行空间")
                    Text("AI 隔离空间", color = JevisMuted)
                }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selected.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标应用") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        apps.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { selected = option; expanded = false },
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("外部操作前确认")
                        Text("发送、发布、提交等动作前询问", style = MaterialTheme.typography.bodySmall, color = JevisMuted)
                    }
                    Switch(
                        checked = confirm,
                        onCheckedChange = { confirm = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = JevisBlue),
                    )
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { create(instruction, selected.value, confirm) },
            enabled = instruction.trim().length >= 3 && !loading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JevisBlue),
        ) { Text(if (loading) "正在创建…" else "创建并执行") }
        Text(
            "任务将在独立虚拟显示中运行，不占用你正在使用的主屏",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = JevisMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
