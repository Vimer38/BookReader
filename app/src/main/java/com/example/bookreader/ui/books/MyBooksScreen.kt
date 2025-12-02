package com.example.bookreader.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bookreader.data.model.LocalBook
import com.example.bookreader.data.model.RemoteBook
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBooksScreen(
    viewModel: BooksViewModel,
    openBook: (bookId: String, title: String, author: String, path: String, format: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            if (event is BooksEvent.Message) {
                snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Мои книги") },
                actions = {
                    IconButton(onClick = viewModel::refreshRemoteBooks) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить список")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                placeholder = { Text("Поиск книг…") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            val booksToDisplay = if (uiState.remoteBooks.isEmpty() && uiState.localBooks.isNotEmpty()) {
                uiState.localBooks.map {
                    RemoteBook(
                        id = it.bookId,
                        title = it.title,
                        author = it.author,
                        fileUrl = "",
                        userId = "",
                        format = it.format.name
                    )
                }
            } else {
                uiState.filtered
            }
            when {
                uiState.isLoading -> LoadingState()
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage.orEmpty(),
                    onRetry = viewModel::refreshRemoteBooks
                )
                booksToDisplay.isEmpty() && uiState.remoteBooks.isEmpty() -> EmptyState("У вас пока нет скачанных книг")
                booksToDisplay.isEmpty() -> EmptyState("Ничего не найдено")
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = booksToDisplay, key = { it.id }) { book ->
                            val isDownloaded = uiState.isDownloaded(book.id)
                            val localBook = uiState.localBooks.firstOrNull { it.bookId == book.id }
                            BookRow(
                                book = book,
                                isDownloaded = isDownloaded,
                                localBook = localBook,
                                downloadProgress = uiState.downloads[book.id] ?: 0,
                                onDownload = { viewModel.downloadBook(book) },
                                onDelete = { viewModel.deleteBook(book.id) },
                                onOpen = {
                                    localBook?.let {
                                        openBook(
                                            it.bookId,
                                            it.title,
                                            it.author,
                                            it.filePath,
                                            it.format.name
                                        )
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

@Composable
private fun BookRow(
    book: RemoteBook,
    isDownloaded: Boolean,
    localBook: LocalBook?,
    downloadProgress: Int,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(enabled = isDownloaded) { onOpen() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .height(72.dp)
                .width(56.dp)
        ) {
            if (book.coverUrl != null) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = book.title.take(1), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = book.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (downloadProgress in 1..99) {
                Text(
                    text = "Загрузка: $downloadProgress%",
                    style = MaterialTheme.typography.labelMedium
                )
            } else if (isDownloaded) {
                Text(
                    text = "Скачано",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(
            onClick = if (isDownloaded) onDelete else onDownload
        ) {
            if (isDownloaded) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
            } else {
                Icon(Icons.Outlined.CloudDownload, contentDescription = "Скачать")
            }
        }
    }
}

