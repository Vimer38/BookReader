package com.example.bookreader.ui.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookreader.data.model.Todo
import com.example.bookreader.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodosUiState(
    val userIdInput: String = DEFAULT_USER_ID.toString(),
    val todos: List<Todo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null
) {
    companion object {
        const val DEFAULT_USER_ID = 1
    }
}

@HiltViewModel
class TodosViewModel @Inject constructor(
    private val repository: TodoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodosUiState())
    val uiState: StateFlow<TodosUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var currentUserId: Int = TodosUiState.DEFAULT_USER_ID

    init {
        observeTodos(TodosUiState.DEFAULT_USER_ID)
        refreshTodos()
    }

    fun updateUserIdInput(value: String) {
        val sanitized = value.filter { it.isDigit() }.take(2)
        _uiState.update { it.copy(userIdInput = sanitized) }
    }

    fun refreshTodos() {
        val userId = _uiState.value.userIdInput.toIntOrNull()
        if (userId == null || userId <= 0) {
            _uiState.update { it.copy(errorMessage = "Введите идентификатор пользователя (положительное число)") }
            return
        }
        if (userId != currentUserId) {
            observeTodos(userId)
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.refreshTodos(userId)
                _uiState.update { it.copy(lastUpdatedAt = System.currentTimeMillis()) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.localizedMessage ?: "Не удалось обновить данные"
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun observeTodos(userId: Int) {
        currentUserId = userId
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeTodos(userId).collect { todos ->
                _uiState.update { it.copy(todos = todos) }
            }
        }
    }
}

