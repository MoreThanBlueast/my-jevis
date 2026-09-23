package com.jevis.mobile.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskEvent(
    val id: String,
    val sequence: Int,
    @SerialName("event_type") val eventType: String,
    val summary: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AgentTask(
    val id: String,
    val instruction: String,
    @SerialName("task_type") val taskType: String,
    @SerialName("target_app") val targetApp: String,
    val recipient: String,
    val subject: String,
    val body: String,
    val status: String,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String,
    val events: List<TaskEvent> = emptyList(),
)

@Serializable
data class CreateTaskRequest(
    val instruction: String,
    val recipient: String,
    val subject: String,
    val body: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

