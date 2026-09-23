package com.jevis.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateTaskScreen(loading: Boolean, error: String?, create: (String, String, String) -> Unit) {
    var recipient by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("你好") }
    var body by remember { mutableStateOf("你好，祝你今天愉快") }
    val valid = recipient.contains("@") && subject.isNotBlank() && body.isNotBlank()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("创建 QQ 邮箱任务", style = MaterialTheme.typography.headlineMedium)
        Text("任务将在 AI 隔离显示中执行；发送属于外部副作用。")
        OutlinedTextField(recipient, { recipient = it }, Modifier.fillMaxWidth(), label = { Text("收件人") }, singleLine = true)
        OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), label = { Text("主题") }, singleLine = true)
        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("正文") }, minLines = 5)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { create(recipient, subject, body) }, enabled = valid && !loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (loading) "正在创建…" else "授权并创建任务")
        }
    }
}

