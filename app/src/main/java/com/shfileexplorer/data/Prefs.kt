package com.shfileexplorer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("sh_file_explorer", Context.MODE_PRIVATE)

    var sortMode: SortMode
        get() = runCatching {
            SortMode.valueOf(prefs.getString(KEY_SORT_MODE, SortMode.NAME.name)!!)
        }.getOrDefault(SortMode.NAME)
        set(value) = prefs.edit().putString(KEY_SORT_MODE, value.name).apply()

    var sortAscending: Boolean
        get() = prefs.getBoolean(KEY_SORT_ASCENDING, true)
        set(value) = prefs.edit().putBoolean(KEY_SORT_ASCENDING, value).apply()

    var gridMode: Boolean
        get() = prefs.getBoolean(KEY_GRID, false)
        set(value) = prefs.edit().putBoolean(KEY_GRID, value).apply()

    var showHidden: Boolean
        get() = prefs.getBoolean(KEY_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDDEN, value).apply()

    var searchSubfolders: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_SUBFOLDERS, false)
        set(value) = prefs.edit().putBoolean(KEY_SEARCH_SUBFOLDERS, value).apply()

    var dualPane: Boolean
        get() = prefs.getBoolean(KEY_DUAL_PANE, false)
        set(value) = prefs.edit().putBoolean(KEY_DUAL_PANE, value).apply()

    var secondaryPath: String?
        get() = prefs.getString(KEY_SECONDARY_PATH, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_SECONDARY_PATH, value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    fun safLocations(): List<SafLocation> =
        decodeSafLocations(prefs.getString(KEY_SAF_LOCATIONS, null))

    fun addSafLocation(location: SafLocation) {
        val updated = (listOf(location) + safLocations().filterNot { it.uri == location.uri })
            .distinctBy { it.uri }
            .take(MAX_SAF_LOCATIONS)
        prefs.edit().putString(KEY_SAF_LOCATIONS, encodeSafLocations(updated)).apply()
    }

    fun removeSafLocation(uri: String) {
        val updated = safLocations().filterNot { it.uri == uri }
        prefs.edit().putString(KEY_SAF_LOCATIONS, encodeSafLocations(updated)).apply()
    }

    fun favorites(): List<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().sorted()

    fun toggleFavorite(path: String): Boolean {
        val current = favorites().toMutableSet()
        val added = if (current.contains(path)) {
            current.remove(path)
            false
        } else {
            current.add(path)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return added
    }

    fun isFavorite(path: String): Boolean = favorites().contains(path)

    fun recentDirectories(): List<String> = decodeList(prefs.getString(KEY_RECENT_DIRS, null))

    fun recentFiles(): List<String> = decodeList(prefs.getString(KEY_RECENT_FILES, null))

    fun rememberDirectory(path: String) {
        val updated = (listOf(path) + recentDirectories())
            .distinct()
            .take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT_DIRS, encodeList(updated)).apply()
    }

    fun rememberFile(path: String) {
        val updated = (listOf(path) + recentFiles())
            .distinct()
            .take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT_FILES, encodeList(updated)).apply()
    }

    fun forgetMissing(paths: Collection<String>) {
        val dirs = recentDirectories().filterNot { it in paths }
        val files = recentFiles().filterNot { it in paths }
        prefs.edit()
            .putString(KEY_RECENT_DIRS, encodeList(dirs))
            .putString(KEY_RECENT_FILES, encodeList(files))
            .apply()
    }

    private fun encodeSafLocations(values: List<SafLocation>): String =
        JSONArray().apply {
            values.forEach { location ->
                put(
                    JSONObject()
                        .put("uri", location.uri)
                        .put("label", location.label),
                )
            }
        }.toString()

    private fun decodeSafLocations(raw: String?): List<SafLocation> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val label = item.optString("label").takeIf { it.isNotBlank() } ?: "Cloud folder"
                SafLocation(uri, label)
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeList(values: List<String>): String =
        JSONArray().apply { values.forEach(::put) }.toString()

    private fun decodeList(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_SORT_ASCENDING = "sort_ascending"
        const val KEY_GRID = "grid"
        const val KEY_HIDDEN = "show_hidden"
        const val KEY_SEARCH_SUBFOLDERS = "search_subfolders"
        const val KEY_DUAL_PANE = "dual_pane"
        const val KEY_SECONDARY_PATH = "secondary_path"
        const val KEY_DARK = "dark_theme"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT_DIRS = "recent_dirs"
        const val KEY_RECENT_FILES = "recent_files"
        const val KEY_SAF_LOCATIONS = "saf_locations"
        const val MAX_RECENT = 12
        const val MAX_SAF_LOCATIONS = 20
    }
}
