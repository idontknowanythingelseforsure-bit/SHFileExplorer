# SH File Explorer

A local-first Android file manager built with Kotlin and Jetpack Compose.

SH File Explorer is an original, independent project. It does not reuse code, assets, icon artwork, themes, or licensing components from other file managers.

Current status: **0.2.0 local feature update**, debug build verified with compile, unit tests, and Android Lint.

---

## Design goals

The project implements a focused file manager with a useful local-storage core. The goal is to keep everyday file-management workflows fast, private, and fully offline.

---

## Features in 0.2.0

### Browsing

- Home dashboard with internal-storage usage, free space, and quick locations.
- Quick access to internal storage, Downloads, Documents, Pictures, DCIM, Music, Movies, Android media, app storage, and secondary volumes on Android 11+.
- List and adaptive grid layouts.
- Breadcrumb navigation, parent navigation, and back handling.
- Favorites and recent folders/files.
- Fast current-folder search by default, with an optional subfolder scope and results streamed in while the search runs.
- Open any folder in a second app window/task, which can be placed beside the first with Android split-screen or a floating-window-capable launcher.
- Show/hide hidden files.
- Sort by name, size, modified date, or type; ascending/descending with folders first.
- Dark-first Material 3 theme with an optional light theme.

### File operations

- Create folders and empty files.
- Rename with name validation.
- Delete files and folders, with confirmation.
- Multi-select and batch copy, move, delete, share, compress, and extract.
- A visible **Extract ZIP** action when a ZIP is selected, plus **Extract here** in each ZIP item menu.
- Clipboard with cut/copy semantics and collision-safe destination names.
- Copy/move destination checks, including rejection of copying or moving a folder into itself.
- File and folder details in a scrollable dialog.
- Open files with an installed viewer through a non-exported `FileProvider`.
- Open images and videos in an in-app preview, with an **Open with another app** button.
- Image and video thumbnails in list, grid, and recent-file views, backed by a bounded in-memory LRU cache.
- Share files through the Android share sheet.
- APK files can be opened with the system package installer.
- ZIP creation and extraction.
- ZIP extraction rejects path traversal (zip-slip), caps entry count and extracted size, and cleans up a partial extraction on failure.

### Privacy and platform behavior

- **No `INTERNET` permission.**
- No analytics, advertising, account system, or telemetry.
- No cloud, FTP, or network code in this version.
- The second-window feature is local Android task/window management; it does not open a network connection.
- `android:allowBackup="false"` and backup/device-transfer exclusions.
- Non-exported `FileProvider`.
- The app asks for Android's user-managed **All files access** on Android 11+. On older supported versions it requests legacy read/write storage permission.
- `REQUEST_INSTALL_PACKAGES` is declared only so the user can explicitly open an APK with the system installer.

---

## Requirements

- Windows, macOS, or Linux with JDK 17.
- Android SDK Platform 35 and Build Tools 35.
- Gradle is provided through the wrapper (Gradle 8.9).

The project uses:

| Setting | Value |
|---|---|
| Package / namespace | `com.shfileexplorer` |
| Debug application ID | `com.shfileexplorer.debug` |
| minSdk | 26 |
| compileSdk / targetSdk | 35 |
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.7.3 |
| Compose BOM | 2024.10.01 |

---

## Build

From this directory:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Current debug artifact:

| Property | Value |
|---|---|
| Size | 16,731,396 bytes |
| SHA-256 | `1A988628E139CCDD1054CF82371E3DFD1EE82FE25936A5A2EE374C58AF4454CF` |

The hash above will change after any source or resource edit, so recompute it for a new build:

```powershell
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

This is a **debug-signed** APK using the standard Android debug certificate. It is suitable for sideloading and testing, not for production distribution. On first launch, grant the requested file access; without it the app shows its permission screen instead of the browser.

### About the Android "harmful app" / Play Protect warning

Android or Play Protect may show a warning such as **"Unsafe app blocked"**, **"This app was built for an unknown developer"**, or a generic harmful-app warning when you install an APK that did not come from the Play Store. That dialog is produced by Android's package installer and Play Protect. SH File Explorer cannot suppress or rewrite it from app code.

The warning is about the build's distribution and reputation, not a message written by this app. To reduce it, use a release-signed build with a protected keystore and distribute through a recognized store or managed-device channel. For local testing, install only builds you built yourself, verify the SHA-256, and do not disable Play Protect globally to force an untrusted APK to install.

Run the verification set:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

### Environment issue on this machine

The machine used to build this project had a malformed `PATH` entry ending in `C:\ffmpeg\bin" /m`. Gradle derives `java.library.path` from `PATH`, which caused JVM test workers to fail at launch. The tests pass after removing that trailing fragment from `PATH` before starting Gradle:

```powershell
$env:PATH = $env:PATH -replace '" /m$', ''
```

This is a host environment issue, not a project requirement.

---

## Test status

Eight JVM unit tests cover the repository layer:

- create/copy/move/delete round trip
- collision-safe copy names
- rejection of copying a folder into itself
- ZIP create/extract round trip
- zip-slip rejection and partial-extraction cleanup
- current-folder vs subfolder search scope, including streamed matches
- symlink-safe deletion (skipped automatically when the host cannot create symlinks)
- filename validation

Verified on 24 October 2026:

```text
:app:testDebugUnitTest   PASSED (8 tests, 7 executed, 1 skipped, 0 failures)
:app:assembleDebug       PASSED
:app:lintDebug           PASSED
```

Runtime UI testing on a physical device or emulator is still pending because no working ADB target was available in this environment. This is documented as the main remaining verification gap.

---

## Project layout

```text
app/src/main/
  AndroidManifest.xml
  java/com/shfileexplorer/
    MainActivity.kt                 Permission gate and app root
    ShFileExplorerApp.kt
    data/
      FileEntry.kt                  File metadata and sorting
      FileIntents.kt                Open/share through FileProvider
      FileRepository.kt             Local file operations and ZIP handling
      Formatting.kt                 Human-readable sizes and dates
      Prefs.kt                      Favorites, recents, and UI preferences
      StorageAccess.kt              All-files and legacy permission helpers
      StorageLocations.kt           Storage roots, quick paths, volume stats
      ThumbnailLoader.kt            Bounded image/video thumbnail cache
    ui/
      ExplorerRoot.kt               State/event wiring and media preview state
      ExplorerViewModel.kt          Screen state and operation orchestration
      components/
        Dialogs.kt                  Name, delete, details, sort, favorites, about
        FileVisuals.kt              File rows, grid cards, icons, item menus
        MediaPreviewDialog.kt       Offline image viewer and video preview
      screens/
        BrowserContent.kt           List/grid/search content
        ExplorerScreen.kt           Scaffold, top bars, actions, dialogs
        HomeScreen.kt               Dashboard, quick access, favorites, recents
        PermissionScreen.kt         First-run file-access explanation
      theme/Theme.kt                Material 3 palette
  res/                              Original SH icon, theme, strings, FileProvider paths
app/src/test/                       Repository unit tests
docs/                               Roadmap and design notes
```

---

## Known limitations

- Local storage only. SMB, FTP/FTPS, SFTP, WebDAV, FTP server mode, cloud providers, and root/Shizuku are not implemented.
- **Open in new window** creates a second Android task; side-by-side display depends on the device's split-screen or freeform-window support. A built-in dual-pane layout is not implemented yet.
- ZIP is the only archive format handled directly. 7z, RAR, TAR, and GZIP are roadmap items.
- The built-in viewer handles images and videos. PDF, audio, office documents, and other formats still open in installed apps.
- Video preview depends on codec support in the platform `VideoView`; some containers may open but fail to play.
- Thumbnails are cached in memory only and are regenerated after the app process is restarted. Very large or malformed media files fall back to a normal file icon.
- Search defaults to the current folder. Subfolder search is optional and can still be slow in very large trees; it is capped at 500 results and 30 seconds.
- No SAF tree-picker fallback. Android 11+ requires the All files access grant.
- Android's platform restrictions still apply to `Android/data` and `Android/obb` for other apps even with All files access.
- No batch rename, folder encryption, app backup, cast support, FTP server, or cloud account integration yet.
- No runtime device verification yet. The media viewer, thumbnail scrolling, second-window behavior, and video playback have not been exercised on a physical device or emulator in this environment.
- Volumes with unusual mount paths may need additional handling on OEM devices.

---

## Documentation

- [Roadmap](docs/ROADMAP.md)
