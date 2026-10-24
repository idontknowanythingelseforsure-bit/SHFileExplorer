package com.shfileexplorer.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shfileexplorer.MainActivity
import com.shfileexplorer.data.BrowserLocation
import com.shfileexplorer.data.ClipboardState
import com.shfileexplorer.data.FileEntry
import com.shfileexplorer.data.SortSpec
import com.shfileexplorer.data.StorageLocations
import com.shfileexplorer.ui.ExplorerUiState
import com.shfileexplorer.ui.ExplorerViewModel
import com.shfileexplorer.ui.PaneId
import com.shfileexplorer.ui.components.AboutDialog
import com.shfileexplorer.ui.components.DeleteDialog
import com.shfileexplorer.ui.components.DetailsDialog
import com.shfileexplorer.ui.components.FavoritesDialog
import com.shfileexplorer.ui.components.FileRowAction
import com.shfileexplorer.ui.components.NamePromptDialog
import com.shfileexplorer.ui.components.SortDialog
import com.shfileexplorer.ui.theme.ShPalette
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    state: ExplorerUiState,
    viewModel: ExplorerViewModel,
    snackbarHostState: SnackbarHostState,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val context = LocalContext.current
    val currentPath = state.activePath.orEmpty()
    val rootLocation = remember(currentPath, context) {
        StorageLocations.rootFor(context, currentPath)
            ?: BrowserLocation("Storage", StorageLocations.primaryRoot().absolutePath)
    }
    val directoryTitle = when {
        state.atHome -> "SH File Explorer"
        currentPath == rootLocation.path -> rootLocation.label
        else -> File(currentPath).name.ifBlank { rootLocation.label }
    }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileEntry>?>(null) }
    var detailsTarget by remember { mutableStateOf<FileEntry?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    fun closeSearch() {
        searchOpen = false
        viewModel.clearSearch()
    }

    fun openPath(path: String) {
        closeSearch()
        viewModel.openDirectory(path)
    }

    fun openInNewWindow(path: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_INITIAL_PATH, path)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    val selected = state.selectedEntries
    val selectedPaths = selected.map(FileEntry::path)

    val handleAction: (FileEntry, FileRowAction) -> Unit = { entry, action ->
        when (action) {
            FileRowAction.OPEN -> viewModel.openEntry(entry)
            FileRowAction.OPEN_IN_NEW_WINDOW -> openInNewWindow(entry.path)
            FileRowAction.SHARE -> viewModel.share(listOf(entry.path))
            FileRowAction.RENAME -> renameTarget = entry
            FileRowAction.COPY -> viewModel.copyToClipboard(listOf(entry.path), cut = false)
            FileRowAction.MOVE -> viewModel.copyToClipboard(listOf(entry.path), cut = true)
            FileRowAction.COMPRESS -> viewModel.compress(listOf(entry.path))
            FileRowAction.EXTRACT -> viewModel.extract(listOf(entry.path))
            FileRowAction.DETAILS -> detailsTarget = entry
            FileRowAction.DELETE -> deleteTargets = listOf(entry)
        }
    }

    BackHandler(
        enabled = state.activeSelection.isNotEmpty() || searchOpen || !state.atHome,
    ) {
        when {
            state.activeSelection.isNotEmpty() -> viewModel.clearSelection()
            searchOpen -> closeSearch()
            else -> viewModel.goUp()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.activeSelection.isNotEmpty()) {
                SelectionTopBar(
                    count = state.activeSelection.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onShare = { viewModel.share(selectedPaths) },
                    onDelete = { deleteTargets = selected },
                    onCopy = { viewModel.copyToClipboard(selectedPaths, cut = false) },
                    onMove = { viewModel.copyToClipboard(selectedPaths, cut = true) },
                    onCompress = { viewModel.compress(selectedPaths) },
                    onExtract = { viewModel.extract(selectedPaths) },
                    onDetails = { selected.singleOrNull()?.let { detailsTarget = it } },
                    canExtract = selected.any { it.isZip },
                    canDetails = selected.size == 1,
                )
            } else {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = if (state.atHome) {
                                        "SH File Explorer"
                                    } else {
                                        directoryTitle
                                    },
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (state.atHome) {
                                        "Local storage"
                                    } else {
                                        "${state.activeEntries.size} ${if (state.activeEntries.size == 1) "item" else "items"}"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                        },
                        navigationIcon = {
                            if (!state.atHome) {
                                IconButton(
                                    onClick = {
                                        if (searchOpen) closeSearch() else viewModel.goUp()
                                    },
                                ) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Up")
                                }
                            }
                        },
                        actions = {
                            if (!state.atHome) {
                                IconButton(
                                    onClick = {
                                        if (searchOpen) closeSearch() else searchOpen = true
                                    },
                                ) {
                                    Icon(
                                        imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                        contentDescription = if (searchOpen) "Close search" else "Search",
                                    )
                                }
                                val favorite = state.activePath in state.favorites
                                IconButton(
                                    onClick = {
                                        state.activePath?.let(viewModel::toggleFavorite)
                                    },
                                ) {
                                    Icon(
                                        imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                                        tint = if (favorite) ShPalette.Amber else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { showTopMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                                }
                                TopOverflowMenu(
                                    expanded = showTopMenu,
                                    onDismiss = { showTopMenu = false },
                                    state = state,
                                    darkTheme = darkTheme,
                                    onNewFile = {
                                        showTopMenu = false
                                        showNewFile = true
                                    },
                                    onNewWindow = {
                                        showTopMenu = false
                                        state.activePath?.let(::openInNewWindow)
                                    },
                                    onToggleDualPane = {
                                        showTopMenu = false
                                        viewModel.toggleDualPane()
                                    },
                                    onSort = {
                                        showTopMenu = false
                                        showSort = true
                                    },
                                    onToggleView = {
                                        showTopMenu = false
                                        viewModel.toggleGrid()
                                    },
                                    onToggleHidden = {
                                        showTopMenu = false
                                        viewModel.toggleHidden()
                                    },
                                    onFavorites = {
                                        showTopMenu = false
                                        showFavorites = true
                                    },
                                    onRefresh = {
                                        showTopMenu = false
                                        if (state.atHome) viewModel.refreshHome() else viewModel.reload()
                                    },
                                    onHome = {
                                        showTopMenu = false
                                        closeSearch()
                                        viewModel.goHome()
                                    },
                                    onToggleTheme = {
                                        showTopMenu = false
                                        onToggleTheme()
                                    },
                                    onAbout = {
                                        showTopMenu = false
                                        showAbout = true
                                    },
                                    onSettings = {
                                        showTopMenu = false
                                        onOpenAppSettings()
                                    },
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )

                    if (searchOpen && !state.atHome) {
                        SearchField(
                            query = state.activeQuery,
                            onQueryChange = viewModel::setQuery,
                            onClose = ::closeSearch,
                            subfolders = state.searchSubfolders,
                            onToggleSubfolders = viewModel::toggleSearchSubfolders,
                        )
                    }

                    if (!state.atHome && !searchOpen && !state.dualPane) {
                        BreadcrumbBar(
                            currentPath = state.activePath.orEmpty(),
                            rootPath = rootLocation.path,
                            rootLabel = rootLocation.label,
                            onOpenPath = ::openPath,
                        )
                    }
                }
            }
        },
        bottomBar = {
            val clipboard = state.clipboard
            if (clipboard != null && !state.atHome) {
                ClipboardBar(
                    clipboard = clipboard,
                    onPaste = viewModel::paste,
                    onClear = viewModel::clearClipboard,
                )
            }
        },
        floatingActionButton = {
            if (!state.atHome && state.activeSelection.isEmpty() && !searchOpen && state.clipboard == null) {
                ExtendedFloatingActionButton(
                    onClick = { showNewFolder = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("New folder") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.atHome -> HomeScreen(
                    state = state,
                    onOpenPath = ::openPath,
                    onOpenFile = viewModel::openEntry,
                    onRemoveFavorite = viewModel::toggleFavorite,
                    onRefresh = viewModel::refreshHome,
                )

                state.dualPane -> DualPaneContent(
                    state = state,
                    onActivatePane = viewModel::setActivePane,
                    onUp = viewModel::goUpInPane,
                    onOpenPath = { pane, path -> viewModel.openDirectoryInPane(pane, path) },
                    onEntryClick = { pane, entry ->
                        viewModel.setActivePane(pane)
                        if (state.pane(pane).selection.isNotEmpty()) {
                            viewModel.toggleSelection(entry.path)
                        } else {
                            viewModel.openEntry(entry)
                        }
                    },
                    onEntryLongClick = { pane, entry ->
                        viewModel.setActivePane(pane)
                        viewModel.toggleSelection(entry.path)
                    },
                    onAction = { pane, entry, action ->
                        viewModel.setActivePane(pane)
                        handleAction(entry, action)
                    },
                    onSortClick = { showSort = true },
                )

                state.isSearching -> SearchContent(
                    state = state,
                    pane = PaneId.PRIMARY,
                    onEntryClick = { entry ->
                        if (state.activeSelection.isNotEmpty()) {
                            viewModel.toggleSelection(entry.path)
                        } else {
                            viewModel.openEntry(entry)
                        }
                    },
                    onEntryLongClick = { viewModel.toggleSelection(it.path) },
                    onAction = handleAction,
                )

                else -> BrowserContent(
                    state = state,
                    pane = PaneId.PRIMARY,
                    onEntryClick = { entry ->
                        if (state.activeSelection.isNotEmpty()) {
                            viewModel.toggleSelection(entry.path)
                        } else {
                            viewModel.openEntry(entry)
                        }
                    },
                    onEntryLongClick = { viewModel.toggleSelection(it.path) },
                    onAction = handleAction,
                    onSortClick = { showSort = true },
                )
            }
        }
    }

    if (showNewFolder) {
        NamePromptDialog(
            title = "New folder",
            label = "Folder name",
            confirmLabel = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                viewModel.createFolder(name)
            },
        )
    }

    if (showNewFile) {
        NamePromptDialog(
            title = "New file",
            label = "File name (for example notes.txt)",
            confirmLabel = "Create",
            onDismiss = { showNewFile = false },
            onConfirm = { name ->
                showNewFile = false
                viewModel.createFile(name)
            },
        )
    }

    renameTarget?.let { target ->
        NamePromptDialog(
            title = "Rename",
            label = "New name",
            initialValue = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                viewModel.rename(target.path, name)
            },
        )
    }

    deleteTargets?.let { targets ->
        DeleteDialog(
            entries = targets,
            onDismiss = { deleteTargets = null },
            onConfirm = {
                deleteTargets = null
                viewModel.delete(targets.map(FileEntry::path))
            },
        )
    }

    detailsTarget?.let { target ->
        DetailsDialog(entry = target, onDismiss = { detailsTarget = null })
    }

    if (showSort) {
        SortDialog(
            current = state.sort,
            onApply = { sort: SortSpec ->
                showSort = false
                viewModel.applySort(sort)
            },
            onDismiss = { showSort = false },
        )
    }

    if (showFavorites) {
        FavoritesDialog(
            favorites = state.favorites,
            onOpen = { path ->
                showFavorites = false
                openPath(path)
            },
            onRemove = viewModel::toggleFavorite,
            onDismiss = { showFavorites = false },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    state.operation?.let { operation ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.width(280.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = operation.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Working on your device",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    subfolders: Boolean,
    onToggleSubfolders: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
    }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(if (subfolders) "Search this folder and subfolders…" else "Search this folder…") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close search")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
        )
        FilterChip(
            selected = subfolders,
            onClick = onToggleSubfolders,
            label = {
                Text(
                    if (subfolders) "Including subfolders" else "This folder only",
                    fontSize = 11.sp,
                )
            },
            modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun BreadcrumbBar(
    currentPath: String,
    rootPath: String,
    rootLabel: String,
    onOpenPath: (String) -> Unit,
) {
    val segments = remember(currentPath, rootPath, rootLabel) {
        buildBreadcrumbs(currentPath, rootPath, rootLabel)
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(segments) { index, segment ->
            TextButton(
                onClick = { onOpenPath(segment.first) },
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = segment.second,
                    color = if (index == segments.lastIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.5.sp,
                    fontWeight = if (index == segments.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
            if (index < segments.lastIndex) {
                Text(
                    text = "›",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onDetails: () -> Unit,
    canExtract: Boolean,
    canDetails: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
            }
        },
        title = {
            Text(
                text = "$count selected",
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
        },
        actions = {
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = "Share")
            }
            if (canExtract) {
                IconButton(onClick = onExtract) {
                    Icon(
                        imageVector = Icons.Rounded.Unarchive,
                        contentDescription = "Extract ZIP",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuAction("Select all", Icons.Rounded.SelectAll) {
                        menuOpen = false
                        onSelectAll()
                    }
                    MenuAction("Copy", Icons.Rounded.ContentCopy) {
                        menuOpen = false
                        onCopy()
                    }
                    MenuAction("Move", Icons.Rounded.ContentCut) {
                        menuOpen = false
                        onMove()
                    }
                    MenuAction("Compress to ZIP", Icons.Rounded.Archive) {
                        menuOpen = false
                        onCompress()
                    }
                    if (canDetails) {
                        MenuAction("Details", Icons.Rounded.Info) {
                            menuOpen = false
                            onDetails()
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun TopOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    state: ExplorerUiState,
    darkTheme: Boolean,
    onNewFile: () -> Unit,
    onNewWindow: () -> Unit,
    onToggleDualPane: () -> Unit,
    onSort: () -> Unit,
    onToggleView: () -> Unit,
    onToggleHidden: () -> Unit,
    onFavorites: () -> Unit,
    onRefresh: () -> Unit,
    onHome: () -> Unit,
    onToggleTheme: () -> Unit,
    onAbout: () -> Unit,
    onSettings: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!state.atHome) {
            MenuAction("New file", Icons.Rounded.Add, onDismiss, onNewFile)
            MenuAction("Open in new window", Icons.AutoMirrored.Rounded.OpenInNew, onDismiss, onNewWindow)
            MenuAction(
                if (state.dualPane) "Use single pane" else "Use dual pane",
                Icons.Rounded.VerticalSplit,
                onDismiss,
                onToggleDualPane,
            )
            MenuAction("Sort by", Icons.Rounded.Sort, onDismiss, onSort)
            MenuAction(
                if (state.grid) "Use list view" else "Use grid view",
                if (state.grid) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                onDismiss,
                onToggleView,
            )
            MenuAction(
                if (state.showHidden) "Hide hidden files" else "Show hidden files",
                if (state.showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                onDismiss,
                onToggleHidden,
            )
        }
        MenuAction("Favorites", Icons.Rounded.Star, onDismiss, onFavorites)
        MenuAction("Refresh", Icons.Rounded.Refresh, onDismiss, onRefresh)
        if (!state.atHome) {
            MenuAction("Go to start", Icons.Rounded.Home, onDismiss, onHome)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        MenuAction(
            if (darkTheme) "Use light theme" else "Use dark theme",
            if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            onDismiss,
            onToggleTheme,
        )
        MenuAction("About", Icons.Rounded.Info, onDismiss, onAbout)
        MenuAction("App settings", Icons.Rounded.Settings, onDismiss, onSettings)
    }
}

@Composable
private fun MenuAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    MenuAction(label, icon, {}, onClick)
}

@Composable
private fun MenuAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = {
            onDismiss()
            onClick()
        },
    )
}

@Composable
private fun ClipboardBar(
    clipboard: ClipboardState,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (clipboard.cut) Icons.Rounded.ContentCut else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${clipboard.paths.size} ${if (clipboard.paths.size == 1) "item" else "items"} ready",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (clipboard.cut) "Move to this folder" else "Copy to this folder",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = onPaste) { Text("Paste here") }
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear clipboard", modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun DualPaneContent(
    state: ExplorerUiState,
    onActivatePane: (PaneId) -> Unit,
    onUp: (PaneId) -> Unit,
    onOpenPath: (PaneId, String) -> Unit,
    onEntryClick: (PaneId, FileEntry) -> Unit,
    onEntryLongClick: (PaneId, FileEntry) -> Unit,
    onAction: (PaneId, FileEntry, FileRowAction) -> Unit,
    onSortClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= 600.dp
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                ExplorerPane(
                    pane = PaneId.PRIMARY,
                    state = state,
                    active = state.activePane == PaneId.PRIMARY,
                    onActivate = { onActivatePane(PaneId.PRIMARY) },
                    onUp = { onUp(PaneId.PRIMARY) },
                    onOpenPath = { onOpenPath(PaneId.PRIMARY, it) },
                    onEntryClick = { onEntryClick(PaneId.PRIMARY, it) },
                    onEntryLongClick = { onEntryLongClick(PaneId.PRIMARY, it) },
                    onAction = { entry, action -> onAction(PaneId.PRIMARY, entry, action) },
                    onSortClick = onSortClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                )
                ExplorerPane(
                    pane = PaneId.SECONDARY,
                    state = state,
                    active = state.activePane == PaneId.SECONDARY,
                    onActivate = { onActivatePane(PaneId.SECONDARY) },
                    onUp = { onUp(PaneId.SECONDARY) },
                    onOpenPath = { onOpenPath(PaneId.SECONDARY, it) },
                    onEntryClick = { onEntryClick(PaneId.SECONDARY, it) },
                    onEntryLongClick = { onEntryLongClick(PaneId.SECONDARY, it) },
                    onAction = { entry, action -> onAction(PaneId.SECONDARY, entry, action) },
                    onSortClick = onSortClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ExplorerPane(
                    pane = PaneId.PRIMARY,
                    state = state,
                    active = state.activePane == PaneId.PRIMARY,
                    onActivate = { onActivatePane(PaneId.PRIMARY) },
                    onUp = { onUp(PaneId.PRIMARY) },
                    onOpenPath = { onOpenPath(PaneId.PRIMARY, it) },
                    onEntryClick = { onEntryClick(PaneId.PRIMARY, it) },
                    onEntryLongClick = { onEntryLongClick(PaneId.PRIMARY, it) },
                    onAction = { entry, action -> onAction(PaneId.PRIMARY, entry, action) },
                    onSortClick = onSortClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                )
                ExplorerPane(
                    pane = PaneId.SECONDARY,
                    state = state,
                    active = state.activePane == PaneId.SECONDARY,
                    onActivate = { onActivatePane(PaneId.SECONDARY) },
                    onUp = { onUp(PaneId.SECONDARY) },
                    onOpenPath = { onOpenPath(PaneId.SECONDARY, it) },
                    onEntryClick = { onEntryClick(PaneId.SECONDARY, it) },
                    onEntryLongClick = { onEntryLongClick(PaneId.SECONDARY, it) },
                    onAction = { entry, action -> onAction(PaneId.SECONDARY, entry, action) },
                    onSortClick = onSortClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExplorerPane(
    pane: PaneId,
    state: ExplorerUiState,
    active: Boolean,
    onActivate: () -> Unit,
    onUp: () -> Unit,
    onOpenPath: (String) -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onAction: (FileEntry, FileRowAction) -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.pane(pane)
    val path = snapshot.path.orEmpty()
    val context = LocalContext.current
    val rootLocation = remember(path, context) {
        StorageLocations.rootFor(context, path)
            ?: BrowserLocation("Storage", StorageLocations.primaryRoot().absolutePath)
    }
    val paneBackground = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Column(modifier = modifier.background(paneBackground)) {
        if (active) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onActivate(); onUp() }) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Up")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onActivate)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = if (path.isBlank()) {
                        "Loading…"
                    } else {
                        File(path).name.ifBlank { rootLocation.label }
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (active) "Active pane" else "Tap to activate",
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 10.sp,
                )
            }
        }

        if (path.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.5.dp,
                )
            }
        } else {
            BreadcrumbBar(
                currentPath = path,
                rootPath = rootLocation.path,
                rootLabel = rootLocation.label,
                onOpenPath = { onActivate(); onOpenPath(it) },
            )
            if (snapshot.query.isNotBlank() || snapshot.searching) {
                SearchContent(
                    state = state,
                    pane = pane,
                    onEntryClick = { onActivate(); onEntryClick(it) },
                    onEntryLongClick = { onActivate(); onEntryLongClick(it) },
                    onAction = { entry, action -> onActivate(); onAction(entry, action) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                BrowserContent(
                    state = state,
                    pane = pane,
                    onEntryClick = { onActivate(); onEntryClick(it) },
                    onEntryLongClick = { onActivate(); onEntryLongClick(it) },
                    onAction = { entry, action -> onActivate(); onAction(entry, action) },
                    onSortClick = { onActivate(); onSortClick() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun buildBreadcrumbs(
    currentPath: String,
    rootPath: String,
    rootLabel: String,
): List<Pair<String, String>> {
    if (currentPath.isBlank()) return emptyList()
    val segments = mutableListOf(rootPath to rootLabel)
    if (currentPath == rootPath) return segments
    if (!currentPath.startsWith(rootPath)) {
        segments.add(currentPath to File(currentPath).name.ifBlank { currentPath })
        return segments
    }
    val relative = currentPath.removePrefix(rootPath).trim('/')
    if (relative.isBlank()) return segments
    var accumulated = rootPath
    for (part in relative.split('/')) {
        if (part.isBlank()) continue
        accumulated = "$accumulated/$part"
        segments.add(accumulated to part)
    }
    return segments
}
