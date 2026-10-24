package com.shfileexplorer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shfileexplorer.data.BrowserLocation
import com.shfileexplorer.data.ClipboardState
import com.shfileexplorer.data.FileEntry
import com.shfileexplorer.data.FileRepository
import com.shfileexplorer.data.OperationState
import com.shfileexplorer.data.Prefs
import com.shfileexplorer.data.SortMode
import com.shfileexplorer.data.SortSpec
import com.shfileexplorer.data.StorageLocations
import com.shfileexplorer.data.StorageStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

data class ExplorerUiState(
    val atHome: Boolean = true,
    val currentPath: String? = null,
    val entries: List<FileEntry> = emptyList(),
    val selection: Set<String> = emptySet(),
    val sort: SortSpec = SortSpec(),
    val grid: Boolean = false,
    val showHidden: Boolean = false,
    val searchSubfolders: Boolean = false,
    val query: String = "",
    val searchResults: List<FileEntry> = emptyList(),
    val searching: Boolean = false,
    val loading: Boolean = false,
    val clipboard: ClipboardState? = null,
    val operation: OperationState? = null,
    val favorites: List<String> = emptyList(),
    val recentDirectories: List<String> = emptyList(),
    val recentFiles: List<String> = emptyList(),
    val quickLocations: List<BrowserLocation> = emptyList(),
    val stats: StorageStats? = null,
) {
    val isSearching: Boolean get() = query.isNotBlank()
    val selectedEntries: List<FileEntry>
        get() = entries.filter { it.path in selection } +
            searchResults.filter { it.path in selection && entries.none { entry -> entry.path == it.path } }
}

sealed interface ExplorerEvent {
    data class Message(val text: String) : ExplorerEvent
    data class OpenFile(val entry: FileEntry) : ExplorerEvent
    data class PreviewFile(val entry: FileEntry) : ExplorerEvent
    data class ShareFiles(val entries: List<FileEntry>) : ExplorerEvent
}

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val repository = FileRepository()

    private val _ui = MutableStateFlow(
        ExplorerUiState(
            sort = SortSpec(prefs.sortMode, prefs.sortAscending),
            grid = prefs.gridMode,
            showHidden = prefs.showHidden,
            searchSubfolders = prefs.searchSubfolders,
        ),
    )
    val ui = _ui.asStateFlow()

    private val _events = MutableSharedFlow<ExplorerEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var searchRequestId = 0

    init {
        refreshHome()
    }

    fun refreshHome() {
        searchRequestId++
        val app = getApplication<Application>()
        val favorites = prefs.favorites().filter { File(it).isDirectory }
        val recentDirs = prefs.recentDirectories().filter { File(it).isDirectory }
        val recentFiles = prefs.recentFiles().filter { File(it).isFile }
        _ui.update {
            it.copy(
                atHome = true,
                currentPath = null,
                entries = emptyList(),
                selection = emptySet(),
                query = "",
                searchResults = emptyList(),
                searching = false,
                loading = false,
                favorites = favorites,
                recentDirectories = recentDirs,
                recentFiles = recentFiles,
                quickLocations = StorageLocations.quickLocations(app),
                stats = StorageLocations.statsFor(StorageLocations.primaryRoot().absolutePath),
            )
        }
    }

    fun openDirectory(path: String, remember: Boolean = true) {
        searchRequestId++
        loadJob?.cancel()
        searchJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    atHome = false,
                    currentPath = path,
                    entries = emptyList(),
                    loading = true,
                    query = "",
                    searchResults = emptyList(),
                    searching = false,
                    selection = emptySet(),
                )
            }
            runCatching { repository.listDirectory(File(path), _ui.value.showHidden) }
                .onSuccess { entries ->
                    val sorted = _ui.value.sort.apply(entries)
                    _ui.update {
                        it.copy(
                            entries = sorted,
                            loading = false,
                            selection = emptySet(),
                        )
                    }
                    if (remember) prefs.rememberDirectory(path)
                    refreshHomeMetadata()
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    _ui.update { it.copy(loading = false) }
                    emitMessage(failure.userMessage())
                }
        }
    }

    fun reload() {
        val path = _ui.value.currentPath ?: return
        openDirectory(path, remember = false)
    }

    fun goHome() {
        searchRequestId++
        loadJob?.cancel()
        searchJob?.cancel()
        refreshHome()
    }

    fun goUp() {
        val path = _ui.value.currentPath ?: return
        val current = File(path)
        if (current.parentFile == null ||
            StorageLocations.isStorageRoot(getApplication(), current.absolutePath)
        ) {
            goHome()
        } else {
            openDirectory(current.parentFile!!.absolutePath)
        }
    }

    fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            openDirectory(entry.path)
        } else {
            prefs.rememberFile(entry.path)
            if (entry.isPreviewable) {
                _events.tryEmit(ExplorerEvent.PreviewFile(entry))
            } else {
                _events.tryEmit(ExplorerEvent.OpenFile(entry))
            }
        }
    }

    fun navigateToResult(entry: FileEntry) {
        if (entry.isDirectory) {
            openDirectory(entry.path)
        } else {
            openDirectory(File(entry.path).parent ?: return)
        }
    }

    fun toggleSelection(path: String) {
        _ui.update {
            val selection = if (path in it.selection) it.selection - path else it.selection + path
            it.copy(selection = selection)
        }
    }

    fun selectAll() {
        _ui.update {
            val paths = if (it.isSearching) it.searchResults else it.entries
            it.copy(selection = paths.map(FileEntry::path).toSet())
        }
    }

    fun clearSelection() {
        _ui.update { it.copy(selection = emptySet()) }
    }

    fun setQuery(rawQuery: String) {
        searchJob?.cancel()
        val requestId = ++searchRequestId
        _ui.update {
            it.copy(
                query = rawQuery,
                selection = emptySet(),
                searchResults = emptyList(),
                searching = false,
            )
        }
        if (rawQuery.isBlank()) return
        val root = _ui.value.currentPath ?: return
        searchJob = viewModelScope.launch {
            delay(250)
            if (searchRequestId != requestId || _ui.value.query != rawQuery) return@launch
            _ui.update { it.copy(searching = true, searchResults = emptyList()) }
            val partial = ArrayList<FileEntry>()
            val result = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                repository.search(
                    root = File(root),
                    query = rawQuery,
                    showHidden = _ui.value.showHidden,
                    subfolders = _ui.value.searchSubfolders,
                    onMatch = { entry ->
                        partial.add(entry)
                        if (partial.size == 1 || partial.size % 12 == 0) {
                            if (searchRequestId == requestId && _ui.value.query == rawQuery) {
                                val snapshot = partial.toList()
                                val sort = _ui.value.sort
                                _ui.update { state -> state.copy(searchResults = sort.apply(snapshot)) }
                            }
                        }
                    },
                )
            }
            if (searchRequestId != requestId || _ui.value.query != rawQuery) return@launch
            val sort = _ui.value.sort
            if (result == null) {
                _ui.update {
                    it.copy(searchResults = sort.apply(partial), searching = false)
                }
                emitMessage("Search timed out. Try this folder only or a narrower folder.")
            } else {
                _ui.update {
                    it.copy(searchResults = sort.apply(result), searching = false)
                }
            }
        }
    }

    fun toggleSearchSubfolders() {
        val enabled = !_ui.value.searchSubfolders
        prefs.searchSubfolders = enabled
        _ui.update { it.copy(searchSubfolders = enabled) }
        val query = _ui.value.query
        if (query.isNotBlank()) setQuery(query)
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchRequestId++
        _ui.update {
            it.copy(
                query = "",
                searchResults = emptyList(),
                searching = false,
                selection = emptySet(),
            )
        }
    }

    fun setSort(mode: SortMode) {
        val current = _ui.value.sort
        val sort = if (current.mode == mode) {
            current.copy(ascending = !current.ascending)
        } else {
            SortSpec(mode, ascending = true)
        }
        prefs.sortMode = sort.mode
        prefs.sortAscending = sort.ascending
        _ui.update {
            it.copy(
                sort = sort,
                entries = sort.apply(it.entries),
                searchResults = sort.apply(it.searchResults),
            )
        }
    }

    fun applySort(sort: SortSpec) {
        prefs.sortMode = sort.mode
        prefs.sortAscending = sort.ascending
        _ui.update {
            it.copy(
                sort = sort,
                entries = sort.apply(it.entries),
                searchResults = sort.apply(it.searchResults),
            )
        }
    }

    fun toggleGrid() {
        val grid = !_ui.value.grid
        prefs.gridMode = grid
        _ui.update { it.copy(grid = grid) }
    }

    fun toggleHidden() {
        val showHidden = !_ui.value.showHidden
        prefs.showHidden = showHidden
        _ui.update { it.copy(showHidden = showHidden) }
        if (!_ui.value.atHome) reload()
    }

    fun toggleFavorite(path: String) {
        val added = prefs.toggleFavorite(path)
        refreshHomeMetadata()
        emitMessage(if (added) "Added to favorites." else "Removed from favorites.")
    }

    fun createFolder(rawName: String) {
        val parent = _ui.value.currentPath ?: return
        runOperation("Creating folder") {
            repository.createFolder(File(parent), rawName)
            reloadCurrent()
            emitMessage("Folder created.")
        }
    }

    fun createFile(rawName: String) {
        val parent = _ui.value.currentPath ?: return
        runOperation("Creating file") {
            repository.createTextFile(File(parent), rawName)
            reloadCurrent()
            emitMessage("File created.")
        }
    }

    fun rename(path: String, rawName: String) {
        runOperation("Renaming") {
            repository.rename(File(path), rawName)
            reloadCurrent()
            clearSelectionInternal()
            emitMessage("Renamed.")
        }
    }

    fun delete(paths: Collection<String>) {
        if (paths.isEmpty()) return
        runOperation("Deleting") {
            repository.delete(paths.map(::File)) { label ->
                _ui.update { it.copy(operation = OperationState(label)) }
            }
            reloadCurrent()
            clearSelectionInternal()
            refreshHomeMetadata()
            emitMessage(if (paths.size == 1) "Item deleted." else "${paths.size} items deleted.")
        }
    }

    fun copyToClipboard(paths: Collection<String>, cut: Boolean) {
        if (paths.isEmpty()) return
        _ui.update { it.copy(clipboard = ClipboardState(paths.toList(), cut)) }
        emitMessage(if (cut) "Ready to move." else "Copied to clipboard.")
    }

    fun paste() {
        val clipboard = _ui.value.clipboard ?: return
        val destination = _ui.value.currentPath ?: return
        runOperation(if (clipboard.cut) "Moving" else "Copying") {
            val sources = clipboard.paths.map(::File).filter { it.exists() }
            if (sources.isEmpty()) {
                _ui.update { it.copy(clipboard = null) }
                throw IOException("The clipboard items no longer exist.")
            }
            if (clipboard.cut) {
                repository.move(sources, File(destination)) { label ->
                    _ui.update { it.copy(operation = OperationState(label)) }
                }
            } else {
                repository.copy(sources, File(destination)) { label ->
                    _ui.update { it.copy(operation = OperationState(label)) }
                }
            }
            if (clipboard.cut) _ui.update { it.copy(clipboard = null) }
            reloadCurrent()
            refreshHomeMetadata()
            emitMessage(if (clipboard.cut) "Moved." else "Copied.")
        }
    }

    fun clearClipboard() {
        _ui.update { it.copy(clipboard = null) }
    }

    fun compress(paths: Collection<String>) {
        val destination = _ui.value.currentPath ?: return
        if (paths.isEmpty()) return
        runOperation("Compressing") {
            val suggested = if (paths.size == 1) {
                File(paths.first()).nameWithoutExtension.ifBlank { "archive" }
            } else {
                "archive"
            }
            val archive = repository.compress(
                sources = paths.map(::File),
                destination = File(destination),
                archiveName = suggested,
            ) { label -> _ui.update { it.copy(operation = OperationState(label)) } }
            reloadCurrent()
            emitMessage("Created ${archive.name}.")
        }
    }

    fun extract(paths: Collection<String>) {
        val destination = _ui.value.currentPath ?: return
        if (paths.isEmpty()) return
        runOperation("Extracting") {
            val archives = paths.map(::File).filter {
                it.isFile && it.name.substringAfterLast('.', "").equals("zip", ignoreCase = true)
            }
            if (archives.isEmpty()) throw IOException("Select at least one ZIP archive.")
            for (archive in archives) {
                repository.extractZip(archive, File(destination)) { label ->
                    _ui.update { it.copy(operation = OperationState(label)) }
                }
            }
            reloadCurrent()
            emitMessage(if (archives.size == 1) "Extracted." else "${archives.size} archives extracted.")
        }
    }

    fun share(paths: Collection<String>) {
        val entries = paths.mapNotNull { path ->
            _ui.value.entries.firstOrNull { it.path == path }
                ?: _ui.value.searchResults.firstOrNull { it.path == path }
                ?: File(path).takeIf { it.exists() }?.let(FileEntry::from)
        }.filterNot(FileEntry::isDirectory)
        if (entries.isEmpty()) {
            emitMessage("Folders cannot be shared directly. Compress them to a ZIP first.")
            return
        }
        _events.tryEmit(ExplorerEvent.ShareFiles(entries))
    }

    fun refreshHomeMetadata() {
        val favorites = prefs.favorites().filter { File(it).isDirectory }
        val recentDirs = prefs.recentDirectories().filter { File(it).isDirectory }
        val recentFiles = prefs.recentFiles().filter { File(it).isFile }
        _ui.update {
            it.copy(
                favorites = favorites,
                recentDirectories = recentDirs,
                recentFiles = recentFiles,
                stats = StorageLocations.statsFor(StorageLocations.primaryRoot().absolutePath),
            )
        }
    }

    private suspend fun reloadCurrent() {
        val snapshot = _ui.value
        val path = snapshot.currentPath ?: return
        val entries = repository.listDirectory(File(path), snapshot.showHidden)
        val sorted = snapshot.sort.apply(entries)
        _ui.update {
            it.copy(
                entries = sorted,
                selection = emptySet(),
            )
        }
        prefs.rememberDirectory(path)
        // Do not make file operations wait for a long subfolder search. The
        // search restarts in the background and streams refreshed results.
        if (snapshot.isSearching) setQuery(snapshot.query)
    }

    private fun clearSelectionInternal() {
        _ui.update { it.copy(selection = emptySet()) }
    }

    private fun runOperation(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(operation = OperationState(label)) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                emitMessage(failure.userMessage())
                if (failure is SecurityException) refreshHomeMetadata()
            } finally {
                _ui.update { it.copy(operation = null) }
            }
        }
    }

    private fun emitMessage(message: String) {
        _events.tryEmit(ExplorerEvent.Message(message))
    }

    private fun Throwable.userMessage(): String = when (this) {
        is IOException -> message ?: "The operation could not be completed."
        is SecurityException -> "Access denied. Check file permissions."
        else -> message ?: "Something went wrong."
    }

    private companion object {
        const val SEARCH_TIMEOUT_MS = 30_000L
    }
}
