package com.example.bookreader.ui.upload

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookreader.data.local.BookFileManager
import com.example.bookreader.data.model.BookFormat
import com.example.bookreader.data.storage.YandexStorageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UploadUiState(
    val title: String = "",
    val author: String = "",
    val fileName: String? = null,
    val fileUri: Uri? = null,
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val errorMessage: String? = null
)

sealed class UploadEvent {
    data class Message(val text: String) : UploadEvent()
    data object Success : UploadEvent()
}

class UploadViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val fileManager = BookFileManager(application)
    private val storageManager = YandexStorageManager(application)

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private val _events = Channel<UploadEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun updateTitle(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun updateAuthor(value: String) {
        _uiState.value = _uiState.value.copy(author = value)
    }

    fun selectFile(uri: Uri?, name: String?) {
        _uiState.value = _uiState.value.copy(fileUri = uri, fileName = name)
    }

    fun upload() {
        val currentUser = auth.currentUser ?: run {
            viewModelScope.launch { _events.send(UploadEvent.Message("Необходима авторизация")) }
            return
        }
        val title = _uiState.value.title.trim()
        val author = _uiState.value.author.trim()
        val fileUri = _uiState.value.fileUri
        val fileName = _uiState.value.fileName

        if (title.isBlank() || author.isBlank() || fileUri == null || fileName.isNullOrBlank()) {
            viewModelScope.launch {
                _events.send(UploadEvent.Message("Заполните все поля и выберите файл"))
            }
            return
        }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val allowed = setOf("txt", "pdf", "epub")
        if (extension !in allowed) {
            viewModelScope.launch {
                _events.send(UploadEvent.Message("Поддерживаются только .txt, .pdf и .epub файлы"))
            }
            return
        }

        val format = BookFormat.fromFileName(fileName)
        if (format == BookFormat.TXT || format == BookFormat.PDF || format == BookFormat.EPUB) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isUploading = true, progress = 0f, errorMessage = null)
                val bookId = UUID.randomUUID().toString()
                val storagePath = "books/${currentUser.uid}/$bookId.${format.primaryExtension}"
                try {
                    val downloadUrl = storageManager.upload(
                        uri = fileUri,
                        contentResolver = getApplication<Application>().contentResolver,
                        objectKey = storagePath
                    ) { fraction ->
                        _uiState.value = _uiState.value.copy(progress = fraction)
                    }
                    firestore.collection("books")
                        .document(bookId)
                        .set(
                            mapOf(
                                "title" to title,
                                "author" to author,
                                "fileUrl" to downloadUrl,
                                "userId" to currentUser.uid,
                                "format" to format.name,
                                "createdAt" to System.currentTimeMillis()
                            )
                        )
                        .await()

                    withContext(Dispatchers.IO) {
                        fileManager.importExternalBook(
                            bookId = bookId,
                            title = title,
                            author = author,
                            format = format,
                            contentResolver = getApplication<Application>().contentResolver,
                            sourceUri = fileUri
                        )
                    }

                    _uiState.value = UploadUiState()
                    _events.send(UploadEvent.Success)

                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        errorMessage = e.localizedMessage
                    )
                    _events.send(UploadEvent.Message(e.localizedMessage ?: "Ошибка загрузки"))
                }
            }
        } else {
            viewModelScope.launch {
                _events.send(UploadEvent.Message("Поддерживаются только .txt, .pdf и .epub файлы"))
            }
        }
    }
}

