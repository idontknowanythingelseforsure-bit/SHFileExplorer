package com.shfileexplorer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileEntryTest {

    @Test
    fun safEntryIsMarkedAndHasNoLocalFile() {
        val entry = FileEntry(
            path = "content://com.example.documents/tree/primary%3ADownload/document/primary%3ADownload%2Fnotes.txt",
            name = "notes.txt",
            isDirectory = false,
            size = 12L,
            lastModified = 0L,
            mimeType = "text/plain",
            isHidden = false,
        )

        assertTrue(entry.isSaf)
        assertNull(entry.file)
        assertFalse(entry.isDirectory)
    }

    @Test
    fun localEntryStillExposesAFile() {
        val entry = FileEntry(
            path = "C:\\notes\\local.txt",
            name = "local.txt",
            isDirectory = false,
            size = 4L,
            lastModified = 0L,
            mimeType = "text/plain",
            isHidden = false,
        )

        assertFalse(entry.isSaf)
        assertTrue(entry.file != null)
    }
}
