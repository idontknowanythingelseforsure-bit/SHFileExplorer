package com.shfileexplorer.ui.components

import android.graphics.Bitmap
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shfileexplorer.data.FileEntry
import com.shfileexplorer.data.ThumbnailLoader
import com.shfileexplorer.data.formatBytes

/**
 * A lightweight, offline media viewer. Images are decoded with sampling so very
 * large camera files do not allocate a full-size bitmap; videos use the platform
 * VideoView and therefore need no extra media dependency.
 */
@Composable
fun MediaPreviewDialog(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (entry.isVideo) {
                VideoPreview(entry)
            } else {
                ImagePreview(entry)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close preview",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenExternal) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open with another app",
                        tint = Color.White,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = entry.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBytes(entry.size),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ImagePreview(entry: FileEntry) {
    val context = LocalContext.current
    var finished by remember(entry.path, entry.lastModified) { mutableStateOf(false) }
    var image by remember(entry.path, entry.lastModified) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.path, entry.lastModified) {
        val uri = entry.uri
        val file = entry.file
        image = when {
            uri != null -> ThumbnailLoader.loadUri(context, uri, PREVIEW_MAX_PX)
            file != null -> ThumbnailLoader.load(file, PREVIEW_MAX_PX)
            else -> null
        }
        finished = true
    }
    val bitmap = image
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 58.dp, bottom = 58.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = entry.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            !finished -> CircularProgressIndicator(color = Color.White)
            else -> Text(
                text = "This image could not be previewed.",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun VideoPreview(entry: FileEntry) {
    val context = LocalContext.current
    val videoView = remember(entry.path) {
        VideoView(context).apply {
            val uri = entry.uri
            if (uri != null) setVideoURI(uri) else setVideoPath(entry.path)
            val controller = MediaController(context)
            controller.setMediaPlayer(this)
            setMediaController(controller)
            setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = true
                start()
            }
        }
    }

    DisposableEffect(videoView) {
        onDispose {
            videoView.stopPlayback()
            videoView.setMediaController(null)
        }
    }

    AndroidView(
        factory = { videoView },
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 58.dp, bottom = 58.dp),
    )
}

private const val PREVIEW_MAX_PX = 2048
