package com.example.bookreader.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookreader.data.storage.YandexStorageManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val isUploadingPhoto: Boolean = false
)

sealed class ProfileEvent {
    data class Message(val text: String) : ProfileEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val yandexStorage: YandexStorageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refreshUser()
    }

    private fun refreshUser() {
        val user = auth.currentUser ?: return
        _uiState.value = ProfileUiState(
            displayName = user.displayName ?: "Без имени",
            email = user.email.orEmpty(),
            photoUrl = user.photoUrl?.toString()
        )
    }

    fun uploadPhoto(uri: Uri) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true)
            try {
                val objectKey = "avatars/${user.uid}/${System.currentTimeMillis()}.jpg"
                val publicUrl = yandexStorage.upload(
                    uri = uri,
                    contentResolver = context.contentResolver,
                    objectKey = objectKey,
                    onProgress = {}
                )
                _uiState.value = _uiState.value.copy(photoUrl = publicUrl, isUploadingPhoto = false)
                _events.send(ProfileEvent.Message("Фото обновлено"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploadingPhoto = false)
                _events.send(ProfileEvent.Message(e.localizedMessage ?: "Не удалось загрузить фото"))
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        auth.signOut()
        onLoggedOut()
    }
}
