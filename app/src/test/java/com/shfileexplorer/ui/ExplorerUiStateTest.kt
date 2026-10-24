package com.shfileexplorer.ui

import com.shfileexplorer.data.FileEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerUiStateTest {

    private fun entry(path: String): FileEntry = FileEntry(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = false,
        size = 1L,
        lastModified = 1L,
        mimeType = "text/plain",
        isHidden = false,
    )

    @Test
    fun activePanePropertiesFollowTheSelectedPane() {
        val primary = entry("/primary/one.txt")
        val secondary = entry("/secondary/two.txt")
        val state = ExplorerUiState(
            currentPath = "/primary",
            entries = listOf(primary),
            selection = setOf(primary.path),
            dualPane = true,
            activePane = PaneId.PRIMARY,
            secondaryPath = "/secondary",
            secondaryEntries = listOf(secondary),
            secondarySelection = setOf(secondary.path),
        )

        assertEquals("/primary", state.activePath)
        assertEquals(listOf(primary), state.activeEntries)
        assertEquals(setOf(primary.path), state.activeSelection)
        assertEquals(listOf(primary), state.selectedEntries)

        val switched = state.copy(activePane = PaneId.SECONDARY)

        assertEquals("/secondary", switched.activePath)
        assertEquals(listOf(secondary), switched.activeEntries)
        assertEquals(setOf(secondary.path), switched.activeSelection)
        assertEquals(listOf(secondary), switched.selectedEntries)
    }

    @Test
    fun selectedEntriesIncludeSearchResultsWithoutDuplicates() {
        val listed = entry("/folder/listed.txt")
        val result = entry("/folder/sub/result.txt")
        val state = ExplorerUiState(
            currentPath = "/folder",
            entries = listOf(listed),
            selection = setOf(listed.path, result.path),
            query = "result",
            searchResults = listOf(result),
        )

        assertEquals(listOf(listed, result), state.selectedEntries)
        assertEquals("result", state.activeQuery)
        assertEquals(true, state.isSearching)
    }
}
