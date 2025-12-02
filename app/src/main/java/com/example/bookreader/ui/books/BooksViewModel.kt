package com.example.bookreader.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookreader.data.model.BookFormat
import com.example.bookreader.data.model.LocalBook
import com.example.bookreader.data.model.RemoteBook
import com.example.bookreader.data.repository.BookRepository
import com.example.bookreader.data.storage.YandexStorageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

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

@HiltViewModel
class BooksViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val bookRepository: BookRepository,
    private val storageManager: YandexStorageManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    private val _events = Channel<BooksEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        observeBooks()
        observeLocalBooks()
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
            bookRepository.getAllBooks().collect { books ->
                _uiState.value = _uiState.value.copy(localBooks = books)
            }
        }
    }
    
    private fun observeLocalBooks() {
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.getAllBooks().collect { books ->
                _uiState.value = _uiState.value.copy(localBooks = books)
            }
        }
    }

    fun refreshRemoteBooks() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Необходима авторизация")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val snapshot = firestore.collection("books")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                val books = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(RemoteBook::class.java)?.copy(id = doc.id)
                }
                _uiState.value = _uiState.value.copy(
                    remoteBooks = books,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Не удалось обновить список"
                )
            }
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
            val destination = bookRepository.getFile(remoteBook.id, format)
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
                bookRepository.insertRemoteBook(remoteBook, format, destination)
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
            val success = bookRepository.deleteBook(bookId)
            if (success) {
                refreshLocal()
                _events.send(BooksEvent.Message("Файл удалён"))
            } else {
                _events.send(BooksEvent.Message("Не удалось удалить файл"))
            }
        }
    }
}
