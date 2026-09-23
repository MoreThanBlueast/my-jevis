package com.jevis.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jevis.mobile.data.TaskRepository
import com.jevis.mobile.model.AgentTask
import com.jevis.mobile.model.CreateTaskRequest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskUiState(
    val tasks: List<AgentTask> = emptyList(),
    val selected: AgentTask? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class TaskViewModel(private val repository: TaskRepository = TaskRepository()) : ViewModel() {
    private val _state = MutableStateFlow(TaskUiState())
    val state: StateFlow<TaskUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { repository.list() }
            .onSuccess { _state.value = _state.value.copy(tasks = it, loading = false) }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }

    fun load(id: String) = viewModelScope.launch {
        runCatching { repository.get(id) }
            .onSuccess { _state.value = _state.value.copy(selected = it, error = null) }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    fun create(
        instruction: String,
        targetApp: String?,
        confirmBeforeExternalAction: Boolean,
        onCreated: (String) -> Unit,
    ) =
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val request = CreateTaskRequest(
                instruction = instruction.trim(),
                targetApp = targetApp?.takeUnless { it == "AUTO" },
                confirmationPolicy = if (confirmBeforeExternalAction) {
                    "BEFORE_EXTERNAL_ACTION"
                } else {
                    "PREAUTHORIZED"
                },
                idempotencyKey = UUID.randomUUID().toString(),
            )
            runCatching { repository.create(request) }
                .onSuccess {
                    _state.value = _state.value.copy(loading = false, selected = it)
                    refresh()
                    onCreated(it.id)
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }

    fun cancel(id: String) = viewModelScope.launch {
        runCatching { repository.cancel(id) }
            .onSuccess { _state.value = _state.value.copy(selected = it); refresh() }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    fun resolveApproval(taskId: String, approvalId: String, approve: Boolean) =
        viewModelScope.launch {
            runCatching { repository.resolveApproval(taskId, approvalId, approve) }
                .onSuccess { _state.value = _state.value.copy(selected = it); refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
}
