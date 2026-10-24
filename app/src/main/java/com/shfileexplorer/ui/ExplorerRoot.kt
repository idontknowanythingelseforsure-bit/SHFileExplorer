package com.shfileexplorer.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shfileexplorer.data.FileEntry
import com.shfileexplorer.data.FileIntents
import com.shfileexplorer.ui.components.MediaPreviewDialog
import com.shfileexplorer.ui.screens.ExplorerScreen
import kotlinx.coroutines.launch

@Composable
fun ExplorerRoot(
    darkTheme: Boolean,
    initialPath: String?,
    onToggleTheme: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ExplorerViewModel = viewModel()
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var previewEntry by remember { mutableStateOf<FileEntry?>(null) }
    var initialPathOpened by rememberSaveable(initialPath) { mutableStateOf(false) }

    LaunchedEffect(initialPath) {
        if (!initialPathOpened && !initialPath.isNullOrBlank()) {
            initialPathOpened = true
            viewModel.openDirectory(initialPath)
        }
    }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                is ExplorerEvent.Message -> {
                    snackbarHostState.showSnackbar(event.text, withDismissAction = true)
                }

                is ExplorerEvent.OpenFile -> {
                    val result = FileIntents.open(context, event.entry)
                    if (result.isFailure) {
                        snackbarHostState.showSnackbar(
                            "No app is available to open this file.",
                            withDismissAction = true,
                        )
                    }
                }

                is ExplorerEvent.PreviewFile -> previewEntry = event.entry

                is ExplorerEvent.ShareFiles -> {
                    val result = FileIntents.share(context, event.entries)
                    if (result.isFailure) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                result.exceptionOrNull()?.message ?: "Could not share these files.",
                                withDismissAction = true,
                            )
                        }
                    }
                }
            }
        }
    }

    ExplorerScreen(
        state = state,
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        darkTheme = darkTheme,
        onToggleTheme = onToggleTheme,
        onOpenAppSettings = onOpenAppSettings,
    )

    previewEntry?.let { entry ->
        MediaPreviewDialog(
            entry = entry,
            onDismiss = { previewEntry = null },
            onOpenExternal = {
                val result = FileIntents.open(context, entry)
                if (result.isSuccess) {
                    previewEntry = null
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "No app is available to open this file.",
                            withDismissAction = true,
                        )
                    }
                }
            },
        )
    }
}
