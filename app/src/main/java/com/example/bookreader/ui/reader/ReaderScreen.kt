package com.example.bookreader.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookreader.data.model.BookFormat
import com.example.bookreader.data.preferences.ReaderTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.progress, listState.layoutInfo.totalItemsCount) {
        if (uiState.progress > 0f && listState.layoutInfo.totalItemsCount > 0) {
            val index = (uiState.progress * listState.layoutInfo.totalItemsCount).toInt().coerceIn(0, listState.layoutInfo.totalItemsCount - 1)
            listState.scrollToItem(index)
        }
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val backgroundColor = when (uiState.preferences.theme) {
        ReaderTheme.SYSTEM -> MaterialTheme.colorScheme.background
        ReaderTheme.LIGHT -> Color.White
        ReaderTheme.DARK -> Color(0xFF121212)
        ReaderTheme.SEPIA -> Color(0xFFF5ECD9)
    }

    LaunchedEffect(listState, uiState.textContent.size, uiState.pdfPages.size) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.layoutInfo.totalItemsCount }
            .collect { (firstVisible, total) ->
                if (total > 0) {
                    val fraction = firstVisible.toFloat() / total.toFloat()
                    viewModel.updateProgress(fraction)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.FormatSize, contentDescription = "Настройки")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(uiState.progress * 100).toInt()}% прочитано",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.errorMessage ?: "Ошибка")
                }
            }
            else -> {
                val preferences = uiState.preferences
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding()
                    )
                ) {
                    if (uiState.format == BookFormat.PDF) {
                        itemsIndexed(uiState.pdfPages) { _, page ->
                            Image(
                                bitmap = page.bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth, // ← Теперь безопасно так делать
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            )
                        }
                    } else {

                        itemsIndexed(
                            items = uiState.textContent,
                            key = { _, _ ->
                                "${preferences.fontScale}_${preferences.lineHeight}"
                            }
                        ) { _, paragraph ->
                            Text(
                                text = paragraph,
                                fontSize = 16.sp * preferences.fontScale,
                                lineHeight = 20.sp * preferences.lineHeight,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false }
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Размер шрифта", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = uiState.preferences.fontScale,
                    onValueChange = { viewModel.updateFontScale(it.coerceIn(0.8f, 2.0f)) },
                    valueRange = 0.8f..2.0f
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Межстрочный интервал", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = uiState.preferences.lineHeight,
                    onValueChange = { viewModel.updateLineHeight(it.coerceIn(1.0f, 2.0f)) },
                    valueRange = 1.0f..2.0f
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Тема", style = MaterialTheme.typography.titleMedium)
                Row {
                    ReaderTheme.values().forEach { theme ->
                        FilterChip(
                            onClick = { viewModel.updateTheme(theme) },
                            label = { Text(themeLabel(theme)) },
                            selected = uiState.preferences.theme == theme,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun themeLabel(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.SYSTEM -> "Система"
    ReaderTheme.LIGHT -> "Светлая"
    ReaderTheme.DARK -> "Тёмная"
    ReaderTheme.SEPIA -> "Сепия"
}

