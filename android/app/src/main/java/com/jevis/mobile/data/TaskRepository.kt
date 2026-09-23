package com.jevis.mobile.data

import com.jevis.mobile.model.AgentTask
import com.jevis.mobile.model.CreateTaskRequest

class TaskRepository(private val api: TaskApi = TaskApi()) {
    suspend fun list(): List<AgentTask> = api.listTasks()
    suspend fun get(id: String): AgentTask = api.getTask(id)
    suspend fun create(request: CreateTaskRequest): AgentTask = api.createTask(request)
    suspend fun cancel(id: String): AgentTask = api.cancelTask(id)
    suspend fun resolveApproval(taskId: String, approvalId: String, approve: Boolean): AgentTask =
        api.resolveApproval(taskId, approvalId, approve)
}
