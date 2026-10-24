package com.shfileexplorer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shfileexplorer.data.FileEntry
import com.shfileexplorer.ui.ExplorerUiState
import com.shfileexplorer.ui.PaneId
import com.shfileexplorer.ui.components.EmptyState
import com.shfileexplorer.ui.components.FileGridItem
import com.shfileexplorer.ui.components.FileRow
import com.shfileexplorer.ui.components.FileRowAction
import java.io.File

@Composable
fun BrowserContent(
    state: ExplorerUiState,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onAction: (FileEntry, FileRowAction) -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
    pane: PaneId = PaneId.PRIMARY,
) {
    val snapshot = state.pane(pane)
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${snapshot.entries.size} ${if (snapshot.entries.size == 1) "item" else "items"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSortClick) {
                Icon(
                    imageVector = Icons.Rounded.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(5.dp))
                Text(
                    text = "${state.sort.mode.label} ${if (state.sort.ascending) "↑" else "↓"}",
                    fontSize = 11.5.sp,
                )
            }
        }

        if (snapshot.loading && snapshot.entries.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        when {
            snapshot.loading && snapshot.entries.isEmpty() -> LoadingBox()
            snapshot.entries.isEmpty() -> EmptyState(
                icon = Icons.Rounded.FolderOpen,
                title = "This folder is empty",
                body = "Create a folder or copy files here to get started.",
            )

            state.grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(snapshot.entries, key = { it.path }) { entry ->
                    FileGridItem(
                        entry = entry,
                        selected = entry.path in snapshot.selection,
                        selectionMode = snapshot.selection.isNotEmpty(),
                        onClick = { onEntryClick(entry) },
                        onLongClick = { onEntryLongClick(entry) },
                        onAction = { onAction(entry, it) },
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 110.dp),
            ) {
                items(snapshot.entries, key = { it.path }) { entry ->
                    FileRow(
                        entry = entry,
                        selected = entry.path in snapshot.selection,
                        selectionMode = snapshot.selection.isNotEmpty(),
                        onClick = { onEntryClick(entry) },
                        onLongClick = { onEntryLongClick(entry) },
                        onAction = { onAction(entry, it) },
                    )
                }
            }
        }
    }
}

@Composable
fun SearchContent(
    state: ExplorerUiState,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onAction: (FileEntry, FileRowAction) -> Unit,
    modifier: Modifier = Modifier,
    pane: PaneId = PaneId.PRIMARY,
) {
    val snapshot = state.pane(pane)
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = when {
                snapshot.searching -> "Searching “${snapshot.query.trim()}”…"
                snapshot.searchResults.isEmpty() -> "No matches for “${snapshot.query.trim()}”"
                else -> "${snapshot.searchResults.size} ${if (snapshot.searchResults.size == 1) "result" else "results"} for “${snapshot.query.trim()}”"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        )

        when {
            snapshot.searching && snapshot.searchResults.isEmpty() -> LoadingBox()
            snapshot.searchResults.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Search,
                title = "Nothing found",
                body = if (state.searchSubfolders) {
                    "Try a shorter name or search from a parent folder."
                } else {
                    "Nothing matched in this folder. Tap \"Including subfolders\" above to search deeper."
                },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 110.dp),
            ) {
                items(snapshot.searchResults, key = { it.path }) { entry ->
                    FileRow(
                        entry = entry,
                        selected = entry.path in snapshot.selection,
                        selectionMode = snapshot.selection.isNotEmpty(),
                        onClick = { onEntryClick(entry) },
                        onLongClick = { onEntryLongClick(entry) },
                        onAction = { onAction(entry, it) },
                        subtitleOverride = entry.path.substringBeforeLast(
                            File.separator,
                            entry.path,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Reading files…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
