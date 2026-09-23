package com.jevis.mobile.executor

import com.jevis.mobile.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class DeviceGatewayApi {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun register(registration: DeviceRegistration) {
        client.post("${BuildConfig.API_BASE_URL}api/v1/devices/register") {
            bearerAuth(BuildConfig.DEVICE_GATEWAY_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(registration)
        }
    }

    suspend fun claim(deviceId: String): ExecutorTask? {
        val response = client.post(
            "${BuildConfig.API_BASE_URL}api/v1/devices/$deviceId/next-task"
        ) { bearerAuth(BuildConfig.DEVICE_GATEWAY_TOKEN) }
        return if (response.status == HttpStatusCode.NoContent) null else response.body()
    }

    suspend fun event(deviceId: String, taskId: String, event: ExecutorEvent) {
        client.post("${BuildConfig.API_BASE_URL}api/v1/devices/$deviceId/tasks/$taskId/events") {
            bearerAuth(BuildConfig.DEVICE_GATEWAY_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(event)
        }
    }

    suspend fun nextAction(deviceId: String, taskId: String): DeviceCommand? {
        val response = client.post(
            "${BuildConfig.API_BASE_URL}api/v1/devices/$deviceId/tasks/$taskId/next-action"
        ) { bearerAuth(BuildConfig.DEVICE_GATEWAY_TOKEN) }
        return if (response.status == HttpStatusCode.NoContent) null else response.body()
    }

    suspend fun completeAction(deviceId: String, commandId: String, result: CommandResult) {
        client.post(
            "${BuildConfig.API_BASE_URL}api/v1/devices/$deviceId/commands/$commandId/result"
        ) {
            bearerAuth(BuildConfig.DEVICE_GATEWAY_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(result)
        }
    }
}
