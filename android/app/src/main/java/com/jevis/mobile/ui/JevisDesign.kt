package com.jevis.mobile.ui

import androidx.compose.ui.graphics.Color

val JevisBlue = Color(0xFF2455D6)
val JevisBlueLight = Color(0xFF2F66E8)
val JevisBackground = Color(0xFFF6F8FC)
val JevisOutline = Color(0xFFD7DFEA)
val JevisText = Color(0xFF172033)
val JevisMuted = Color(0xFF64748B)
val JevisSuccess = Color(0xFF16A34A)
val JevisDanger = Color(0xFFDC2626)
val JevisWarning = Color(0xFFF59E0B)

fun String.taskTitle(): String {
    val clean = trim().replace(Regex("\\s+"), " ")
    return when {
        clean.isBlank() -> "未命名任务"
        clean.length <= 18 -> clean
        else -> clean.take(18) + "…"
    }
}

fun String.appLabel(): String = when (this) {
    "com.tencent.androidqqmail" -> "QQ 邮箱"
    "com.android.calendar" -> "日历"
    "com.tencent.mm" -> "微信"
    "com.android.browser" -> "浏览器"
    "com.miui.notes" -> "便签"
    "com.autonavi.minimap" -> "高德地图"
    "com.miui.gallery" -> "相册"
    "com.android.contacts" -> "联系人"
    "AUTO", "" -> "自动识别"
    else -> substringAfterLast('.').ifBlank { "目标应用" }
}

fun String.statusLabel(): String = when (this) {
    "CREATED" -> "已创建"
    "QUEUED" -> "排队中"
    "PREPARING_DEVICE" -> "准备沙盒"
    "RUNNING" -> "执行中"
    "WAITING_CONFIRMATION" -> "等待确认"
    "VERIFYING" -> "验证结果"
    "SUCCEEDED" -> "已完成"
    "FAILED" -> "失败"
    "CANCELED" -> "已取消"
    "EXPIRED" -> "已过期"
    "NEEDS_REVIEW" -> "需要处理"
    else -> this
}
