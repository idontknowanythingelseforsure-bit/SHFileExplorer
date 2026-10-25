package com.shfileexplorer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shfileexplorer.data.FileEntry
import com.shfileexplorer.data.ThumbnailLoader
import com.shfileexplorer.data.formatBytes
import com.shfileexplorer.data.formatDate
import com.shfileexplorer.ui.theme.ShPalette

enum class FileRowAction {
    OPEN,
    OPEN_IN_NEW_WINDOW,
    SHARE,
    RENAME,
    COPY,
    MOVE,
    COMPRESS,
    EXTRACT,
    DETAILS,
    DELETE,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (FileRowAction) -> Unit,
    modifier: Modifier = Modifier,
    subtitleOverride: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
        } else {
            Color.Transparent
        },
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIcon(entry)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitleOverride ?: fileSubtitle(entry),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(22.dp),
                )
            } else if (!selectionMode) {
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    FileActionMenu(
                        expanded = menuOpen,
                        entry = entry,
                        onDismiss = { menuOpen = false },
                        onAction = {
                            menuOpen = false
                            onAction(it)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (FileRowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        ),
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                FileIcon(entry, size = 50.dp)
                Box {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp),
                        )
                    } else if (!selectionMode) {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                    FileActionMenu(
                        expanded = menuOpen,
                        entry = entry,
                        onDismiss = { menuOpen = false },
                        onAction = {
                            menuOpen = false
                            onAction(it)
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Column {
                Text(
                    text = entry.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (entry.isDirectory) "Folder" else formatBytes(entry.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FileActionMenu(
    expanded: Boolean,
    entry: FileEntry,
    onDismiss: () -> Unit,
    onAction: (FileRowAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (entry.isPreviewable) {
            MenuItem("Preview", Icons.Rounded.Visibility) { onAction(FileRowAction.OPEN) }
        } else {
            MenuItem("Open", Icons.AutoMirrored.Rounded.OpenInNew) { onAction(FileRowAction.OPEN) }
        }
        if (entry.isDirectory) {
            MenuItem("Open in new window", Icons.AutoMirrored.Rounded.OpenInNew) {
                onAction(FileRowAction.OPEN_IN_NEW_WINDOW)
            }
        }
        if (!entry.isDirectory) {
            MenuItem("Share", Icons.Rounded.Share) { onAction(FileRowAction.SHARE) }
        }
        MenuItem("Rename", Icons.Rounded.Edit) { onAction(FileRowAction.RENAME) }
        MenuItem("Copy", Icons.Rounded.ContentCopy) { onAction(FileRowAction.COPY) }
        MenuItem("Move", Icons.AutoMirrored.Rounded.DriveFileMove) { onAction(FileRowAction.MOVE) }
        if (!entry.isSaf) {
            MenuItem("Compress to ZIP", Icons.Rounded.Archive) { onAction(FileRowAction.COMPRESS) }
            if (entry.isZip) {
                MenuItem("Extract here", Icons.Rounded.Unarchive) { onAction(FileRowAction.EXTRACT) }
            }
        }
        MenuItem("Details", Icons.Rounded.Info) { onAction(FileRowAction.DETAILS) }
        MenuItem("Delete", Icons.Rounded.Delete, danger = true) { onAction(FileRowAction.DELETE) }
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
fun FileIcon(entry: FileEntry, size: androidx.compose.ui.unit.Dp = 42.dp) {
    val tint = fileTint(entry)
    val density = LocalDensity.current
    val targetPx = with(density) { (size * 2.4f).roundToPx() }
    var thumbnail by remember(entry.path, entry.lastModified, targetPx) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(entry.path, entry.lastModified, targetPx) {
        val file = entry.file
        thumbnail = if (entry.isPreviewable && !entry.isSaf && file != null) {
            ThumbnailLoader.load(file, targetPx)?.asImageBitmap()
        } else {
            null
        }
    }
    val bitmap = thumbnail
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 46.dp) 16.dp else 12.dp))
            .background(tint.copy(alpha = if (entry.isDirectory) 0.18f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            ComposeImage(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (entry.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.46f),
                    )
                }
            }
        } else {
            Icon(
                imageVector = fileIcon(entry),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}

fun fileIcon(entry: FileEntry): ImageVector {
    if (entry.isDirectory) return Icons.Rounded.Folder
    return when (entry.extension) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg" -> Icons.Rounded.Image
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v" -> Icons.Rounded.Movie
        "mp3", "m4a", "wav", "flac", "ogg", "aac", "opus" -> Icons.Rounded.MusicNote
        "pdf" -> Icons.Rounded.PictureAsPdf
        "zip", "7z", "rar", "tar", "gz", "gzip", "bz2", "xz" -> Icons.Rounded.Archive
        "apk", "apks", "xapk" -> Icons.Rounded.Android
        "kt", "kts", "java", "js", "ts", "tsx", "py", "rs", "go", "c", "cpp", "h", "json", "xml", "html", "css", "sh" -> Icons.Rounded.Code
        "txt", "md", "log", "doc", "docx", "rtf", "csv", "ini", "conf" -> Icons.Rounded.Description
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

fun fileTint(entry: FileEntry): Color {
    if (entry.isDirectory) return ShPalette.Teal
    return when (entry.extension) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg" -> Color(0xFFFF8FB1)
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v" -> Color(0xFFB69CFF)
        "mp3", "m4a", "wav", "flac", "ogg", "aac", "opus" -> ShPalette.Amber
        "pdf" -> Color(0xFFFF7B72)
        "zip", "7z", "rar", "tar", "gz", "gzip", "bz2", "xz" -> Color(0xFFFFA94D)
        "apk", "apks", "xapk" -> Color(0xFF7EDB8B)
        "kt", "kts", "java", "js", "ts", "tsx", "py", "rs", "go", "c", "cpp", "h", "json", "xml", "html", "css", "sh" -> Color(0xFF66C7FF)
        "txt", "md", "log", "doc", "docx", "rtf", "csv", "ini", "conf" -> Color(0xFF89B4FF)
        else -> ShPalette.TextSecondary
    }
}

fun fileSubtitle(entry: FileEntry): String {
    if (entry.isDirectory) return "Folder"
    val size = formatBytes(entry.size)
    val date = formatDate(entry.lastModified)
    return "$size  ·  $date"
}

