package com.example.bookreader.ui.books

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookreader.data.local.BookFileManager
import com.example.bookreader.data.model.BookFormat
import com.example.bookreader.data.model.LocalBook
import com.example.bookreader.data.model.RemoteBook
import com.example.bookreader.data.storage.YandexStorageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class BooksUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val remoteBooks: List<RemoteBook> = emptyList(),
    val localBooks: List<LocalBook> = emptyList(),
    val downloads: Map<String, Int> = emptyMap(),
    val errorMessage: String? = null
) {
    val filtered: List<RemoteBook> = if (query.isBlank()) remoteBooks else {
        remoteBooks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true)
        }
    }

    fun isDownloaded(bookId: String): Boolean = localBooks.any { it.bookId == bookId }
}

sealed class BooksEvent {
    data class Message(val text: String) : BooksEvent()
}

class BooksViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val fileManager = BookFileManager(application)
    private val storageManager = YandexStorageManager(application)

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    private val _events = Channel<BooksEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        observeBooks()
        refreshLocal()
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }

    fun updateQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun refreshLocal() {
        viewModelScope.launch(Dispatchers.IO) {
            val books = fileManager.listLocalBooks()
            _uiState.value = _uiState.value.copy(localBooks = books)
        }
    }

    private fun observeBooks() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Необходима авторизация")
            return
        }
        listenerRegistration?.remove()
        listenerRegistration = firestore.collection("books")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage
                    )
                    return@addSnapshotListener
                }
                val books = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(RemoteBook::class.java)?.copy(id = doc.id)
                }.orEmpty()
                _uiState.value = _uiState.value.copy(
                    remoteBooks = books,
                    isLoading = false,
                    errorMessage = null
                )
            }
    }

    fun downloadBook(remoteBook: RemoteBook) {
        if (auth.currentUser == null) {
            viewModelScope.launch {
                _events.send(BooksEvent.Message("Необходима авторизация"))
            }
            return
        }
        viewModelScope.launch {
            if (remoteBook.fileUrl.isBlank()) {
                _events.send(BooksEvent.Message("Файл недоступен"))
                return@launch
            }
            val format = BookFormat.fromString(remoteBook.format)
            val destination = fileManager.destinationFor(remoteBook.id, format)
            _uiState.value = _uiState.value.copy(
                downloads = _uiState.value.downloads + (remoteBook.id to 0)
            )
            try {
                storageManager.download(
                    fileUrl = remoteBook.fileUrl,
                    destination = destination
                ) { progress ->
                    _uiState.value = _uiState.value.copy(
                        downloads = _uiState.value.downloads + (remoteBook.id to progress)
                    )
                }
                fileManager.persistRemoteBook(remoteBook, format, destination)
                refreshLocal()
                _events.send(BooksEvent.Message("Книга загружена"))
            } catch (e: Exception) {
                destination.delete()
                _events.send(BooksEvent.Message(e.localizedMessage ?: "Ошибка загрузки"))
            } finally {
                _uiState.value = _uiState.value.copy(
                    downloads = _uiState.value.downloads - remoteBook.id
                )
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            val success = fileManager.deleteBook(bookId)
            if (success) {
                refreshLocal()
                _events.send(BooksEvent.Message("Файл удалён"))
            } else {
                _events.send(BooksEvent.Message("Не удалось удалить файл"))
            }
        }
    }
}

