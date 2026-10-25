package com.shfileexplorer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shfileexplorer.data.BrowserLocation
import com.shfileexplorer.data.FileEntry
import com.shfileexplorer.data.SafLocation
import com.shfileexplorer.data.StorageStats
import com.shfileexplorer.data.formatBytes
import com.shfileexplorer.data.formatDate
import com.shfileexplorer.ui.ExplorerUiState
import com.shfileexplorer.ui.components.FileIcon
import com.shfileexplorer.ui.theme.ShPalette
import java.io.File

@Composable
fun HomeScreen(
    state: ExplorerUiState,
    onOpenPath: (String) -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSaf: (SafLocation) -> Unit,
    onAddSaf: () -> Unit,
    onRemoveSaf: (SafLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { StorageCard(state.stats, onRefresh) }
        item {
            SectionHeader(
                title = "Quick access",
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
            )
        }
        items(state.quickLocations.chunked(2), key = { row -> row.first().path }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { location ->
                    QuickTile(
                        location = location,
                        onClick = { onOpenPath(location.path) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            SectionHeader(
                title = "Cloud & SAF",
                modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
            )
        }
        if (state.safLocations.isEmpty()) {
            item {
                AddCloudRow(
                    label = "Add a cloud folder",
                    detail = "Google Drive, OneDrive, or another document provider",
                    onClick = onAddSaf,
                )
            }
        } else {
            items(state.safLocations, key = { "saf-${it.uri}" }) { location ->
                SafShortcutRow(
                    location = location,
                    onClick = { onOpenSaf(location) },
                    onRemove = { onRemoveSaf(location) },
                )
            }
            item {
                AddCloudRow(
                    label = "Add another cloud folder",
                    detail = "Uses Android's Storage Access Framework",
                    onClick = onAddSaf,
                )
            }
        }

        if (state.favorites.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Favorites",
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                )
            }
            items(state.favorites, key = { "fav-$it" }) { path ->
                FolderShortcutRow(
                    path = path,
                    label = File(path).name.ifBlank { "Storage" },
                    favorite = true,
                    onClick = { onOpenPath(path) },
                    onRemoveFavorite = { onRemoveFavorite(path) },
                )
            }
        }

        if (state.recentDirectories.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recent folders",
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                )
            }
            items(state.recentDirectories.take(5), key = { "recent-$it" }) { path ->
                FolderShortcutRow(
                    path = path,
                    label = File(path).name.ifBlank { path },
                    favorite = false,
                    onClick = { onOpenPath(path) },
                    onRemoveFavorite = null,
                )
            }
        }

        if (state.recentFiles.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recent files",
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                )
            }
            items(state.recentFiles.take(6), key = { "file-$it" }) { path ->
                val file = File(path)
                if (file.exists()) {
                    RecentFileRow(FileEntry.from(file), onClick = { onOpenFile(FileEntry.from(file)) })
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No account  •  No INTERNET permission  •  SAF cloud access",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun StorageCard(stats: StorageStats?, onRefresh: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            ShPalette.Teal.copy(alpha = 0.18f),
                            ShPalette.Surface.copy(alpha = 0.2f),
                            ShPalette.Amber.copy(alpha = 0.06f),
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(ShPalette.Teal.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = ShPalette.Teal,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Internal storage",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = if (stats != null) {
                                "${formatBytes(stats.freeBytes)} free of ${formatBytes(stats.totalBytes)}"
                            } else {
                                "Storage details unavailable"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh storage",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { stats?.usedFraction ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(50)),
                    color = ShPalette.Teal,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stats?.let { "${formatBytes(it.usedBytes)} used" } ?: "—",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                    )
                    Text(
                        text = "On this device",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    location: BrowserLocation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDownloads = location.label == "Downloads"
    val icon = if (isDownloads) Icons.Rounded.Download else Icons.Rounded.Folder
    val tint = if (isDownloads) ShPalette.Amber else ShPalette.Teal
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = location.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderShortcutRow(
    path: String,
    label: String,
    favorite: Boolean,
    onClick: () -> Unit,
    onRemoveFavorite: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ShPalette.Teal.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = ShPalette.Teal,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = path,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favorite && onRemoveFavorite != null) {
            IconButton(onClick = onRemoveFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Remove favorite",
                    tint = ShPalette.Amber,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun SafShortcutRow(
    location: SafLocation,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ShPalette.Amber.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = null,
                tint = ShPalette.Amber,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = location.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Cloud folder  •  Storage Access Framework",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove cloud location",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddCloudRow(
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentFileRow(entry: FileEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(entry, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatBytes(entry.size)}  ·  ${formatDate(entry.lastModified)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Rounded.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        )
    }
}
