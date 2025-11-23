package com.example.bookreader.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Html
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.bookreader.data.model.BookFormat
import com.example.bookreader.data.preferences.ReaderPreferences
import com.example.bookreader.data.preferences.ReaderTheme
import com.example.bookreader.data.preferences.ReadingPositionStore
import com.example.bookreader.data.preferences.ReadingPreferencesStore
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val format: BookFormat = BookFormat.TXT,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val textContent: List<String> = emptyList(),
    val pdfPages: List<PdfPage> = emptyList(),
    val preferences: ReaderPreferences = ReaderPreferences(),
    val progress: Float = 0f
)

data class PdfPage(val index: Int, val bitmap: Bitmap)

class ReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val preferencesStore = ReadingPreferencesStore(application)
    private val args = ReaderArgs(savedStateHandle)
    private val progressStore = ReadingPositionStore(getApplication())

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            bookId = args.bookId,
            title = args.title,
            author = args.author,
            format = args.format
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        observeProgress()
        loadContent()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesStore.preferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(preferences = prefs)
            }
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            preferencesStore.progressFlow(args.bookId).collect { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }
        }
    }

    private fun loadContent() {
        val path = args.path
        if (path.isBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Файл не найден")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                when (args.format) {
                    BookFormat.TXT -> loadTextContent(File(path))
                    BookFormat.EPUB -> loadEpubContent(File(path))
                    BookFormat.PDF -> loadPdfContent(File(path))
                }
            }.onSuccess {
                val savedProgress = progressStore.getProgress(args.bookId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null,
                    progress = savedProgress
                )

                viewModelScope.launch {
                    delay(100)
                    _uiState.value = _uiState.value
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.localizedMessage ?: "Не удалось открыть книгу"
                )
            }
        }

    }

    private fun loadTextContent(file: File) {
        val text = file.readText()
        val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        _uiState.value = _uiState.value.copy(textContent = paragraphs)
    }

    private fun loadEpubContent(file: File) {
        val builder = StringBuilder()
        ZipInputStream(FileInputStream(file)).use { zip ->
            var entry = zip.nextEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".xhtml", true)) {
                    val entryBuilder = StringBuilder()
                    var read = zip.read(buffer)
                    while (read != -1) {
                        entryBuilder.append(String(buffer, 0, read, Charsets.UTF_8))
                        read = zip.read(buffer)
                    }
                    val htmlText = Html.fromHtml(entryBuilder.toString(), Html.FROM_HTML_MODE_COMPACT).toString()
                    builder.append(htmlText.trim())
                    builder.append("\n\n")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val paragraphs = builder.toString().split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        _uiState.value = _uiState.value.copy(textContent = paragraphs)
    }

    private fun loadPdfContent(file: File) {
        val pages = mutableListOf<PdfPage>()
        val context = getApplication<Application>().applicationContext
        val metrics = context.resources.displayMetrics
        val screenWidthPx = metrics.widthPixels

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val pageWidth = page.width
                        val pageHeight = page.height

                        val scale = screenWidthPx.toFloat() / pageWidth.toFloat()
                        val targetWidth = screenWidthPx
                        val targetHeight = (pageHeight * scale).toInt()

                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        pages.add(PdfPage(i, bitmap))
                    }
                }
            }
        }
        _uiState.value = _uiState.value.copy(pdfPages = pages)
    }

    fun updateFontScale(value: Float) {
        Log.d("FONT", "Setting font scale to $value")
        viewModelScope.launch {
            preferencesStore.setFontScale(value)
        }
    }

    fun updateLineHeight(value: Float) {
        viewModelScope.launch {
            preferencesStore.setLineHeight(value)
        }
    }

    fun updateTheme(theme: ReaderTheme) {
        viewModelScope.launch {
            preferencesStore.setTheme(theme)
        }
    }

    fun updateProgress(progress: Float) {
        Log.d("PROGRESS", "Saving progress: $progress for book ${args.bookId}")
        viewModelScope.launch {
            preferencesStore.setProgress(args.bookId, progress)
            progressStore.saveProgress(args.bookId, progress)
        }
    }
}

private data class ReaderArgs(
    val bookId: String,
    val title: String,
    val author: String,
    val path: String,
    val format: BookFormat
) {
    constructor(handle: SavedStateHandle) : this(
        bookId = handle.get<String>("bookId") ?: "",
        title = decode(handle.get<String>("title")),
        author = decode(handle.get<String>("author")),
        path = decode(handle.get<String>("path")),
        format = BookFormat.fromString(handle.get<String>("format") ?: BookFormat.TXT.name)
    )

    companion object {
        private fun decode(value: String?): String = value?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) } ?: ""
    }
}

