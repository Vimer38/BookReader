package com.example.bookreader.ui.auth

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val isLoading: Boolean = false,
    val isLoginMode: Boolean = true,
    val errorMessage: String? = null
)

sealed class AuthEvent {
    data object Success : AuthEvent()
    data class Error(val message: String) : AuthEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (auth.currentUser != null) {
            viewModelScope.launch { _events.send(AuthEvent.Success) }
        }
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(isLoginMode = !_uiState.value.isLoginMode, errorMessage = null)
    }

    fun submit() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password.trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Введите корректный email")
            return
        }

        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "Пароль должен содержать минимум 6 символов")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                if (_uiState.value.isLoginMode) {
                    auth.signInWithEmailAndPassword(email, password).await()
                } else {
                    val username = _uiState.value.username.trim()
                    if (username.isEmpty()) {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Введите имя пользователя")
                        _events.send(AuthEvent.Error("Введите имя пользователя"))
                        return@launch
                    }

                    auth.createUserWithEmailAndPassword(email, password).await().also { authResult ->
                        authResult.user?.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(username)
                                .build()
                        )?.await()
                    }
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.send(AuthEvent.Success)
            } catch (network: FirebaseNetworkException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Нет подключения к сети")
                _events.send(AuthEvent.Error("Нет подключения к сети"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
                _events.send(AuthEvent.Error(e.message ?: "Не удалось выполнить запрос"))
            }
        }
    }
}
