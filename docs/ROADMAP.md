# Roadmap

SH File Explorer is intentionally being built in layers. The local core should remain useful and fully offline even if later network or cloud modules are added.

---

## 0.1 — Local MVP (baseline)

- [x] Kotlin + Jetpack Compose application
- [x] Dark-first Material 3 interface and original SH icon
- [x] Android 11+ All files access flow; legacy read/write flow for older devices
- [x] Home dashboard with storage statistics and quick locations
- [x] Secondary-volume detection on Android 11+
- [x] List and grid browsing with breadcrumbs
- [x] Favorites, recent folders, recent files, and hidden-file toggle
- [x] Current-folder-first search with optional subfolder scope, streaming results, timeout, and result cap
- [x] Name/size/date/type sorting
- [x] Create folder and empty file
- [x] Rename, delete, copy, move, share, details, and open-with
- [x] Multi-select actions and clipboard
- [x] Collision-safe names and self-copy/self-move protection
- [x] ZIP compression and extraction with zip-slip protection and limits
- [x] APK install intent through the system package installer
- [x] Eight repository unit tests (one host-skipped), successful debug build, successful lint
- [x] Image/video thumbnails and an offline image/video preview
- [x] Open a folder in a second app window for split-screen use
- [x] Scrollable Details and About dialogs
- [ ] Physical-device or emulator UI verification

**Immediate next work:** run the app on an Android 11+ device and an older supported API, then exercise permission grant/revoke, navigation, copy/move collisions, search cancellation, ZIP extraction failures, share/open flows, thumbnail scrolling, media preview, and second-window behavior.

---

## 0.2 — Local depth and polish (in progress)

### Recently completed

- [x] Current-folder-first search with optional subfolder scope and streamed results
- [x] Visible **Extract ZIP** action in the selection bar
- [x] Image and video thumbnails with a bounded memory cache and limited decode concurrency
- [x] Built-in offline image viewer and video preview, with external-open fallback
- [x] Open a folder in a second app window/task for split-screen use
- [x] Scrollable Details and About dialogs

### Reliability

- [ ] Instrumented UI tests for navigation, selection, dialogs, and permission gate
- [ ] Repository tests for cross-volume move fallback and partially failed operations
- [ ] Stress tests for deeply nested folders and large ZIP entry counts
- [ ] Better error recovery when a folder disappears during browsing
- [ ] Operation cancellation and progress reporting
- [ ] Handle OEM mount-point variations and removable-volume edge cases

### Browsing and organization

- [ ] Tabs and optional dual-pane mode for tablets/foldables
- [ ] File shortcuts on the home screen
- [ ] Batch rename with preview
- [ ] Advanced rename patterns (counter, date, extension changes)
- [ ] Optional folder-size calculation with cancellation
- [ ] Custom sort presets and remembered per-folder view state

### Files and formats

- [ ] Archive abstraction: ZIP, TAR, GZIP, 7z read, and RAR read where licensing/engine choice permits
- [ ] Archive browsing before extraction
- [ ] Multiple extraction destinations and conflict policies
- [ ] Text editor for small local text files
- [ ] PDF viewer using platform APIs
- [ ] Audio playback handoff and optional local media browser
- [ ] Checksums (SHA-256) and file-properties enhancements
- [ ] Duplicate-name policy UI: replace, keep both, skip

### Platform

- [ ] SAF tree-picker fallback for users who do not want All files access
- [ ] Release signing and Play Store policy review if distribution is ever considered
- [ ] Localization and RTL audit
- [ ] Accessibility audit (TalkBack labels, large text, contrast)
- [ ] Material You/dynamic color option while keeping the SH palette

---

## 0.3 — Network storage (optional module)

Network support requires `INTERNET` permission. To protect the offline core, network features should ship behind a separate product flavor or optional module:

```text
offline flavor  → no INTERNET permission
network flavor  → SMB/SFTP/FTP/WebDAV
```

Planned transports:

- [ ] SMB2/SMB3 via SMBJ
- [ ] FTP/FTPS via Apache Commons Net
- [ ] SFTP via a maintained SSH library
- [ ] WebDAV/Nextcloud-style endpoints via OkHttp
- [ ] Connection profiles and favorites
- [ ] Android Keystore-backed credential storage
- [ ] Certificate and host-key verification UX
- [ ] Timeouts, retry policy, and explicit cleartext rules
- [ ] Read-only mode per connection
- [ ] Operation queue with cancellation

Security requirements:

- No plaintext credential storage.
- No silent trust of invalid TLS certificates or unknown SSH host keys.
- No telemetry containing host names, usernames, paths, or file names.
- Network errors must not leak credentials into logs.
- Local network access must be explicit and visible.

---

## 0.4 — Cloud providers (optional, separate module)

Cloud features are inherently online and cannot satisfy the offline-core requirement. If added, they should be independent plugins:

- [ ] Google Drive
- [ ] Dropbox
- [ ] OneDrive
- [ ] Box
- [ ] WebDAV-based providers first, proprietary SDKs later

Requirements:

- OAuth tokens stored with Android Keystore protection.
- Clear provider-specific privacy disclosure.
- No background sync by default.
- Revoke/remove account flow.
- No analytics or ad SDKs.

---

## Explicit non-goals

- Reproducing or redistributing code, resources, branding, or icon artwork from other file managers.
- Any license-circumvention mechanism.
- Silent or unrestricted root access in the core app.
- Root/Shizuku privilege escalation as a default feature.
- Analytics, ad SDKs, or telemetry.
- Hidden network activity in the offline build.

Root/Shizuku is not planned for the core. If it is ever explored, it must be a separate, clearly labeled power-user module with a threat model and opt-in consent.

---

## Quality gates for each release

1. `:app:testDebugUnitTest` passes.
2. `:app:assembleDebug` passes.
3. `:app:lintDebug` passes.
4. Manual device pass on API 26, 29, 31, and the current target API.
5. Permission grant, deny, and revoke paths verified.
6. File-operation failure injection verified (no silent data loss).
7. ZIP extraction fuzzed with malformed and hostile archives.
8. No new sensitive permission without a written justification.
9. Release APK signed with a protected keystore, not a debug key.
10. Privacy and permission text reviewed against the shipped manifest.

---

## Success criteria for the local product

- A user can find, open, organize, copy, move, share, compress, and extract local files without an account or network connection.
- The app never needs `INTERNET` permission in its offline build.
- Destructive actions are explicit and recoverable through clear confirmation.
- The UI remains fast with thousands of entries and respects Android back/gesture navigation.
- The app is honest about unsupported formats and permissions rather than failing silently.
