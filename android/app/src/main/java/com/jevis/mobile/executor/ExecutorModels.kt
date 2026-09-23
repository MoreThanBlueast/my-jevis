package com.jevis.mobile.executor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class DeviceRegistration(
    @SerialName("device_id") val deviceId: String,
    val model: String,
    @SerialName("android_version") val androidVersion: String,
    @SerialName("hyperos_version") val hyperOsVersion: String? = null,
    @SerialName("display_id") val displayId: Int? = null,
    @SerialName("profile_user_id") val profileUserId: Int? = null,
    @SerialName("virtual_display_ready") val virtualDisplayReady: Boolean,
    @SerialName("directed_input_ready") val directedInputReady: Boolean,
)

@Serializable
data class ExecutorTask(
    @SerialName("task_id") val taskId: String,
    val instruction: String,
    @SerialName("target_app") val targetApp: String,
    @SerialName("confirmation_policy") val confirmationPolicy: String,
    val recipient: String,
    val subject: String,
    val body: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("required_display_id") val requiredDisplayId: Int,
    @SerialName("required_profile_user_id") val requiredProfileUserId: Int,
)

@Serializable
data class ExecutorEvent(
    val sequence: Int,
    @SerialName("event_type") val eventType: String,
    val summary: String,
    @SerialName("display_id") val displayId: Int,
    @SerialName("profile_user_id") val profileUserId: Int,
    val status: String? = null,
    val payload: JsonObject = buildJsonObject {},
)

@Serializable
data class UiNode(
    val text: String,
    val description: String,
    val viewId: String,
    val className: String,
    val clickable: Boolean,
    val editable: Boolean,
    val bounds: String,
)

@Serializable
data class UiSnapshot(
    val displayId: Int,
    val packageName: String?,
    val nodes: List<UiNode>,
)

@Serializable
data class DeviceCommand(
    val id: String,
    @SerialName("task_id") val taskId: String,
    val sequence: Int,
    @SerialName("action_type") val actionType: String,
    val arguments: JsonObject,
    val summary: String,
    val status: String,
)

@Serializable
data class CommandResult(
    @SerialName("display_id") val displayId: Int,
    @SerialName("profile_user_id") val profileUserId: Int,
    val success: Boolean,
    val summary: String,
    val observation: UiSnapshot,
)
