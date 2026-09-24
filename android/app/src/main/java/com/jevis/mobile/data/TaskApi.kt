package com.jevis.mobile.data

import com.jevis.mobile.BuildConfig
import com.jevis.mobile.model.AgentTask
import com.jevis.mobile.model.CreateTaskRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class TaskApi {
    private val client = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun listTasks(): List<AgentTask> =
        client.get("${BuildConfig.API_BASE_URL}api/v1/tasks").body()

    suspend fun getTask(id: String): AgentTask =
        client.get("${BuildConfig.API_BASE_URL}api/v1/tasks/$id").body()

    suspend fun createTask(request: CreateTaskRequest): AgentTask =
        client.post("${BuildConfig.API_BASE_URL}api/v1/tasks") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun cancelTask(id: String): AgentTask =
        client.post("${BuildConfig.API_BASE_URL}api/v1/tasks/$id/cancel").body()

    suspend fun resumeTask(id: String): AgentTask =
        client.post("${BuildConfig.API_BASE_URL}api/v1/tasks/$id/resume").body()

    suspend fun resolveApproval(taskId: String, approvalId: String, approve: Boolean): AgentTask =
        client.post(
            "${BuildConfig.API_BASE_URL}api/v1/tasks/$taskId/approvals/$approvalId/" +
                if (approve) "approve" else "reject"
        ).body()
}
