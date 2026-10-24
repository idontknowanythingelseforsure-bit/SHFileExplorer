package com.shfileexplorer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shfileexplorer.data.Prefs
import com.shfileexplorer.data.StorageAccess
import com.shfileexplorer.ui.ExplorerRoot
import com.shfileexplorer.ui.screens.PermissionScreen
import com.shfileexplorer.ui.theme.ShFileExplorerTheme

class MainActivity : ComponentActivity() {

    private var initialPath: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialPath = intent?.getStringExtra(EXTRA_INITIAL_PATH)
        enableEdgeToEdge()
        setContent { AppRoot(initialPath = initialPath) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialPath = intent.getStringExtra(EXTRA_INITIAL_PATH)
    }

    companion object {
        const val EXTRA_INITIAL_PATH = "com.shfileexplorer.extra.INITIAL_PATH"
    }
}

@Composable
private fun AppRoot(initialPath: String?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { Prefs(context) }

    var darkTheme by remember { mutableStateOf(prefs.darkTheme) }
    var hasAccess by remember { mutableStateOf(StorageAccess.hasFileAccess(context)) }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAccess = StorageAccess.hasFileAccess(context)
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        hasAccess = StorageAccess.hasFileAccess(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = StorageAccess.hasFileAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                allFilesLauncher.launch(StorageAccess.allFilesSettingsIntent(context))
            }.onFailure {
                runCatching {
                    context.startActivity(StorageAccess.allFilesFallbackIntent())
                }.onFailure {
                    runCatching {
                        context.startActivity(StorageAccess.appSettingsIntent(context))
                    }
                }
            }
        } else {
            legacyPermissionLauncher.launch(StorageAccess.legacyPermissions)
        }
    }

    ShFileExplorerTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (hasAccess) {
                ExplorerRoot(
                    darkTheme = darkTheme,
                    initialPath = initialPath,
                    onToggleTheme = {
                        darkTheme = !darkTheme
                        prefs.darkTheme = darkTheme
                    },
                    onOpenAppSettings = {
                        runCatching { context.startActivity(StorageAccess.appSettingsIntent(context)) }
                    },
                )
            } else {
                PermissionScreen(
                    onGrant = ::requestAccess,
                    onOpenSettings = {
                        runCatching { context.startActivity(StorageAccess.appSettingsIntent(context)) }
                    },
                    onRetry = { hasAccess = StorageAccess.hasFileAccess(context) },
                )
            }
        }
    }
}
