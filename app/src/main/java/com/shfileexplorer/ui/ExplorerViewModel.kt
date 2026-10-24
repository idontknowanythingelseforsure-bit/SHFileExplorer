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

enum class PaneId {
    PRIMARY,
    SECONDARY,
}

data class PaneSnapshot(
    val path: String? = null,
    val entries: List<FileEntry> = emptyList(),
    val selection: Set<String> = emptySet(),
    val loading: Boolean = false,
    val query: String = "",
    val searchResults: List<FileEntry> = emptyList(),
    val searching: Boolean = false,
)

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
    val dualPane: Boolean = false,
    val activePane: PaneId = PaneId.PRIMARY,
    val secondaryPath: String? = null,
    val secondaryEntries: List<FileEntry> = emptyList(),
    val secondarySelection: Set<String> = emptySet(),
    val secondaryLoading: Boolean = false,
    val secondaryQuery: String = "",
    val secondarySearchResults: List<FileEntry> = emptyList(),
    val secondarySearching: Boolean = false,
) {
    val primaryPane: PaneSnapshot
        get() = PaneSnapshot(
            path = currentPath,
            entries = entries,
            selection = selection,
            loading = loading,
            query = query,
            searchResults = searchResults,
            searching = searching,
        )

    val secondaryPane: PaneSnapshot
        get() = PaneSnapshot(
            path = secondaryPath,
            entries = secondaryEntries,
            selection = secondarySelection,
            loading = secondaryLoading,
            query = secondaryQuery,
            searchResults = secondarySearchResults,
            searching = secondarySearching,
        )

    fun pane(pane: PaneId): PaneSnapshot =
        if (pane == PaneId.PRIMARY) primaryPane else secondaryPane

    val activePaneState: PaneSnapshot get() = pane(activePane)
    val activePath: String? get() = activePaneState.path
    val activeEntries: List<FileEntry> get() = activePaneState.entries
    val activeSelection: Set<String> get() = activePaneState.selection
    val activeLoading: Boolean get() = activePaneState.loading
    val activeQuery: String get() = activePaneState.query
    val activeSearchResults: List<FileEntry> get() = activePaneState.searchResults
    val activeSearching: Boolean get() = activePaneState.searching
    val isSearching: Boolean get() = activeQuery.isNotBlank()
    val selectedEntries: List<FileEntry>
        get() = activeEntries.filter { it.path in activeSelection } +
            activeSearchResults.filter {
                it.path in activeSelection && activeEntries.none { entry -> entry.path == it.path }
            }
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
            dualPane = prefs.dualPane,
        ),
    )
    val ui = _ui.asStateFlow()

    private val _events = MutableSharedFlow<ExplorerEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val searchJobs = mutableMapOf<PaneId, Job?>()
    private val loadJobs = mutableMapOf<PaneId, Job?>()
    private val searchRequestIds = mutableMapOf<PaneId, Int>()

    init {
        refreshHome()
    }

    fun refreshHome() {
        nextSearchRequest(PaneId.PRIMARY)
        loadJobs[PaneId.PRIMARY]?.cancel()
        searchJobs[PaneId.PRIMARY]?.cancel()
        val app = getApplication<Application>()
        val favorites = prefs.favorites().filter { File(it).isDirectory }
        val recentDirs = prefs.recentDirectories().filter { File(it).isDirectory }
        val recentFiles = prefs.recentFiles().filter { File(it).isFile }
        _ui.update {
            it.copy(
                atHome = true,
                activePane = PaneId.PRIMARY,
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

    fun toggleDualPane() {
        val state = _ui.value
        val enabled = !state.dualPane
        prefs.dualPane = enabled
        if (!enabled) {
            _ui.update { it.copy(dualPane = false, activePane = PaneId.PRIMARY) }
            emitMessage("Dual pane disabled.")
            return
        }

        val primaryTarget = state.currentPath ?: StorageLocations.primaryRoot().absolutePath
        val secondaryTarget = prefs.secondaryPath
            ?.takeIf { File(it).isDirectory }
            ?: state.secondaryPath?.takeIf { File(it).isDirectory }
            ?: primaryTarget
        _ui.update {
            it.copy(
                dualPane = true,
                activePane = PaneId.PRIMARY,
                secondaryPath = secondaryTarget,
            )
        }
        if (state.atHome || state.currentPath == null) {
            _ui.update { it.copy(atHome = false, currentPath = primaryTarget) }
            openPrimaryDirectory(primaryTarget, remember = false)
        }
        openSecondaryDirectory(secondaryTarget, remember = false)
        emitMessage("Dual pane enabled.")
    }

    fun setActivePane(pane: PaneId) {
        if (pane == PaneId.SECONDARY) {
            val state = _ui.value
            if (!state.dualPane || state.atHome) return
            if (state.secondaryPath == null) {
                val target = state.currentPath ?: return
                _ui.update { it.copy(secondaryPath = target) }
                openSecondaryDirectory(target, remember = false)
            }
        }
        setActivePaneInternal(pane)
    }

    fun openDirectory(path: String, remember: Boolean = true) {
        openDirectoryInPane(_ui.value.activePane, path, remember)
    }

    fun openDirectoryInPane(pane: PaneId, path: String, remember: Boolean = true) {
        if (pane == PaneId.SECONDARY && !_ui.value.dualPane) return
        setActivePaneInternal(pane)
        if (pane == PaneId.SECONDARY) {
            openSecondaryDirectory(path, remember)
        } else {
            openPrimaryDirectory(path, remember)
        }
    }

    private fun openPrimaryDirectory(path: String, remember: Boolean) {
        nextSearchRequest(PaneId.PRIMARY)
        loadJobs[PaneId.PRIMARY]?.cancel()
        searchJobs[PaneId.PRIMARY]?.cancel()
        loadJobs[PaneId.PRIMARY] = viewModelScope.launch {
            updatePaneState(PaneId.PRIMARY) {
                it.copy(
                    path = path,
                    entries = emptyList(),
                    loading = true,
                    query = "",
                    searchResults = emptyList(),
                    searching = false,
                    selection = emptySet(),
                )
            }
            _ui.update { it.copy(atHome = false) }
            runCatching { repository.listDirectory(File(path), _ui.value.showHidden) }
                .onSuccess { entries ->
                    val sorted = _ui.value.sort.apply(entries)
                    updatePaneState(PaneId.PRIMARY) {
                        it.copy(entries = sorted, loading = false, selection = emptySet())
                    }
                    if (remember) prefs.rememberDirectory(path)
                    refreshHomeMetadata()
                    val state = _ui.value
                    if (state.dualPane && state.secondaryPath == null) {
                        openSecondaryDirectory(path, remember = false)
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    updatePaneState(PaneId.PRIMARY) { it.copy(loading = false) }
                    emitMessage(failure.userMessage())
                }
        }
    }

    private fun openSecondaryDirectory(path: String, remember: Boolean) {
        if (!_ui.value.dualPane) return
        nextSearchRequest(PaneId.SECONDARY)
        loadJobs[PaneId.SECONDARY]?.cancel()
        searchJobs[PaneId.SECONDARY]?.cancel()
        loadJobs[PaneId.SECONDARY] = viewModelScope.launch {
            updatePaneState(PaneId.SECONDARY) {
                it.copy(
                    path = path,
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
                    updatePaneState(PaneId.SECONDARY) {
                        it.copy(entries = sorted, loading = false, selection = emptySet())
                    }
                    if (remember) prefs.rememberDirectory(path)
                    prefs.secondaryPath = path
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    updatePaneState(PaneId.SECONDARY) { it.copy(loading = false) }
                    emitMessage(failure.userMessage())
                }
        }
    }

    fun reload() {
        reloadPane(_ui.value.activePane)
    }

    private fun reloadPane(pane: PaneId) {
        val path = _ui.value.pane(pane).path ?: return
        if (pane == PaneId.SECONDARY) {
            openSecondaryDirectory(path, remember = false)
        } else {
            openPrimaryDirectory(path, remember = false)
        }
    }

    fun goHome() {
        refreshHome()
    }

    fun goUp() {
        goUpInPane(_ui.value.activePane)
    }

    fun goUpInPane(pane: PaneId) {
        setActivePaneInternal(pane)
        val path = _ui.value.pane(pane).path ?: return
        val current = File(path)
        if (current.parentFile == null ||
            StorageLocations.isStorageRoot(getApplication(), current.absolutePath)
        ) {
            if (pane == PaneId.PRIMARY) {
                goHome()
            } else {
                setActivePaneInternal(PaneId.PRIMARY)
            }
        } else {
            openDirectoryInPane(pane, current.parentFile!!.absolutePath)
        }
    }

    fun openEntry(entry: FileEntry) {
        openEntryInPane(_ui.value.activePane, entry)
    }

    fun openEntryInPane(pane: PaneId, entry: FileEntry) {
        setActivePaneInternal(pane)
        if (entry.isDirectory) {
            openDirectoryInPane(pane, entry.path)
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
        val pane = _ui.value.activePane
        if (entry.isDirectory) {
            openDirectoryInPane(pane, entry.path)
        } else {
            openDirectoryInPane(pane, File(entry.path).parent ?: return)
        }
    }

    fun toggleSelection(path: String) {
        toggleSelectionInPane(_ui.value.activePane, path)
    }

    fun toggleSelectionInPane(pane: PaneId, path: String) {
        setActivePaneInternal(pane)
        updatePaneState(pane) { snapshot ->
            val selection = if (path in snapshot.selection) {
                snapshot.selection - path
            } else {
                snapshot.selection + path
            }
            snapshot.copy(selection = selection)
        }
    }

    fun selectAll() {
        selectAllInPane(_ui.value.activePane)
    }

    fun selectAllInPane(pane: PaneId) {
        setActivePaneInternal(pane)
        val snapshot = _ui.value.pane(pane)
        val paths = (if (snapshot.query.isNotBlank()) snapshot.searchResults else snapshot.entries)
            .map(FileEntry::path)
            .toSet()
        updatePaneState(pane) { it.copy(selection = paths) }
    }

    fun clearSelection() {
        clearSelectionInPane(_ui.value.activePane)
    }

    fun clearSelectionInPane(pane: PaneId) {
        updatePaneState(pane) { it.copy(selection = emptySet()) }
    }

    fun setQuery(rawQuery: String) {
        setQueryInPane(_ui.value.activePane, rawQuery)
    }

    private fun setQueryInPane(pane: PaneId, rawQuery: String) {
        searchJobs[pane]?.cancel()
        val requestId = nextSearchRequest(pane)
        updatePaneState(pane) {
            it.copy(
                query = rawQuery,
                selection = emptySet(),
                searchResults = emptyList(),
                searching = false,
            )
        }
        if (rawQuery.isBlank()) return
        val root = _ui.value.pane(pane).path ?: return
        searchJobs[pane] = viewModelScope.launch {
            delay(250)
            if (searchRequestIds[pane] != requestId || _ui.value.pane(pane).query != rawQuery) {
                return@launch
            }
            updatePaneState(pane) { it.copy(searching = true, searchResults = emptyList()) }
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
                            if (searchRequestIds[pane] == requestId &&
                                _ui.value.pane(pane).query == rawQuery
                            ) {
                                val snapshot = partial.toList()
                                val sort = _ui.value.sort
                                updatePaneState(pane) {
                                    it.copy(searchResults = sort.apply(snapshot))
                                }
                            }
                        }
                    },
                )
            }
            if (searchRequestIds[pane] != requestId || _ui.value.pane(pane).query != rawQuery) {
                return@launch
            }
            val sort = _ui.value.sort
            if (result == null) {
                updatePaneState(pane) {
                    it.copy(searchResults = sort.apply(partial), searching = false)
                }
                emitMessage("Search timed out. Try this folder only or a narrower folder.")
            } else {
                updatePaneState(pane) {
                    it.copy(searchResults = sort.apply(result), searching = false)
                }
            }
        }
    }

    fun toggleSearchSubfolders() {
        val enabled = !_ui.value.searchSubfolders
        prefs.searchSubfolders = enabled
        _ui.update { it.copy(searchSubfolders = enabled) }
        PaneId.values().forEach { pane ->
            val query = _ui.value.pane(pane).query
            if (query.isNotBlank()) setQueryInPane(pane, query)
        }
    }

    fun clearSearch() {
        clearSearchInPane(_ui.value.activePane)
    }

    private fun clearSearchInPane(pane: PaneId) {
        searchJobs[pane]?.cancel()
        nextSearchRequest(pane)
        updatePaneState(pane) {
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
        applySortToPanes(sort)
    }

    fun applySort(sort: SortSpec) {
        prefs.sortMode = sort.mode
        prefs.sortAscending = sort.ascending
        applySortToPanes(sort)
    }

    private fun applySortToPanes(sort: SortSpec) {
        _ui.update {
            it.copy(
                sort = sort,
                entries = sort.apply(it.entries),
                searchResults = sort.apply(it.searchResults),
                secondaryEntries = sort.apply(it.secondaryEntries),
                secondarySearchResults = sort.apply(it.secondarySearchResults),
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
        if (!_ui.value.atHome) {
            viewModelScope.launch {
                reloadPaneNow(PaneId.PRIMARY)
                if (_ui.value.dualPane) reloadPaneNow(PaneId.SECONDARY)
            }
        }
    }

    fun toggleFavorite(path: String) {
        val added = prefs.toggleFavorite(path)
        refreshHomeMetadata()
        emitMessage(if (added) "Added to favorites." else "Removed from favorites.")
    }

    fun createFolder(rawName: String) {
        val parent = _ui.value.activePath ?: return
        runOperation("Creating folder") {
            repository.createFolder(File(parent), rawName)
            reloadVisiblePanesNow()
            emitMessage("Folder created.")
        }
    }

    fun createFile(rawName: String) {
        val parent = _ui.value.activePath ?: return
        runOperation("Creating file") {
            repository.createTextFile(File(parent), rawName)
            reloadVisiblePanesNow()
            emitMessage("File created.")
        }
    }

    fun rename(path: String, rawName: String) {
        runOperation("Renaming") {
            repository.rename(File(path), rawName)
            reloadVisiblePanesNow()
            emitMessage("Renamed.")
        }
    }

    fun delete(paths: Collection<String>) {
        if (paths.isEmpty()) return
        runOperation("Deleting") {
            repository.delete(paths.map(::File)) { label ->
                _ui.update { it.copy(operation = OperationState(label)) }
            }
            reloadVisiblePanesNow()
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
        val destination = _ui.value.activePath ?: return
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
            reloadVisiblePanesNow()
            refreshHomeMetadata()
            emitMessage(if (clipboard.cut) "Moved." else "Copied.")
        }
    }

    fun clearClipboard() {
        _ui.update { it.copy(clipboard = null) }
    }

    fun compress(paths: Collection<String>) {
        val destination = _ui.value.activePath ?: return
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
            reloadVisiblePanesNow()
            emitMessage("Created ${archive.name}.")
        }
    }

    fun extract(paths: Collection<String>) {
        val destination = _ui.value.activePath ?: return
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
            reloadVisiblePanesNow()
            emitMessage(if (archives.size == 1) "Extracted." else "${archives.size} archives extracted.")
        }
    }

    fun share(paths: Collection<String>) {
        val state = _ui.value
        val snapshot = state.pane(state.activePane)
        val entries = paths.mapNotNull { path ->
            snapshot.entries.firstOrNull { it.path == path }
                ?: snapshot.searchResults.firstOrNull { it.path == path }
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

    private suspend fun reloadPaneNow(pane: PaneId) {
        val snapshot = _ui.value.pane(pane)
        val path = snapshot.path ?: return
        val entries = repository.listDirectory(File(path), _ui.value.showHidden)
        val sorted = _ui.value.sort.apply(entries)
        updatePaneState(pane) {
            it.copy(entries = sorted, selection = emptySet(), loading = false)
        }
        prefs.rememberDirectory(path)
        if (pane == PaneId.SECONDARY) prefs.secondaryPath = path
        if (snapshot.query.isNotBlank()) setQueryInPane(pane, snapshot.query)
    }

    private suspend fun reloadVisiblePanesNow() {
        if (_ui.value.atHome) return
        reloadPaneNow(PaneId.PRIMARY)
        if (_ui.value.dualPane) reloadPaneNow(PaneId.SECONDARY)
    }

    private fun setActivePaneInternal(pane: PaneId) {
        _ui.update { state ->
            if (pane == PaneId.SECONDARY && !state.dualPane) {
                state
            } else if (state.activePane == pane) {
                state
            } else if (state.activePane == PaneId.PRIMARY) {
                state.copy(activePane = pane, selection = emptySet())
            } else {
                state.copy(activePane = pane, secondarySelection = emptySet())
            }
        }
    }

    private fun updatePaneState(pane: PaneId, transform: (PaneSnapshot) -> PaneSnapshot) {
        _ui.update { state ->
            val updated = transform(state.pane(pane))
            if (pane == PaneId.PRIMARY) {
                state.copy(
                    currentPath = updated.path,
                    entries = updated.entries,
                    selection = updated.selection,
                    loading = updated.loading,
                    query = updated.query,
                    searchResults = updated.searchResults,
                    searching = updated.searching,
                )
            } else {
                state.copy(
                    secondaryPath = updated.path,
                    secondaryEntries = updated.entries,
                    secondarySelection = updated.selection,
                    secondaryLoading = updated.loading,
                    secondaryQuery = updated.query,
                    secondarySearchResults = updated.searchResults,
                    secondarySearching = updated.searching,
                )
            }
        }
    }

    private fun nextSearchRequest(pane: PaneId): Int {
        val requestId = (searchRequestIds[pane] ?: 0) + 1
        searchRequestIds[pane] = requestId
        return requestId
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
