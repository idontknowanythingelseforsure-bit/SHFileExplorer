package com.shfileexplorer.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileRepositoryTest {

    private lateinit var root: File
    private lateinit var repository: FileRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("shfe-repository-test").toFile()
        repository = FileRepository()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun createCopyMoveAndDeleteRoundTrip() = runBlocking {
        val source = repository.createFolder(root, "source")
        File(source, "note.txt").writeText("hello from SH")
        File(source, "nested").mkdirs()
        File(source, "nested/data.bin").writeBytes(byteArrayOf(1, 2, 3, 4))

        val destination = repository.createFolder(root, "destination")
        val copied = repository.copy(listOf(source), destination).single()
        assertEquals("source", copied.name)
        assertEquals("hello from SH", File(copied, "note.txt").readText())
        assertEquals(4, File(copied, "nested/data.bin").length())

        val moved = repository.move(listOf(copied), root).single()
        assertEquals("source (2)", moved.name)
        assertFalse(copied.exists())
        assertTrue(moved.exists())

        val deleted = repository.delete(listOf(moved))
        assertEquals(1, deleted)
        assertFalse(moved.exists())
    }

    @Test
    fun copyUsesUniqueNamesWhenDestinationExists() = runBlocking {
        val source = File(root, "photo.jpg").apply { writeText("image") }
        val destination = repository.createFolder(root, "photos")
        val first = repository.copy(listOf(source), destination).single()
        val second = repository.copy(listOf(source), destination).single()

        assertEquals("photo.jpg", first.name)
        assertEquals("photo (2).jpg", second.name)
        assertEquals("image", second.readText())
    }

    @Test
    fun rejectsCopyingFolderIntoItself() = runBlocking {
        val source = repository.createFolder(root, "self")
        File(source, "inside.txt").writeText("x")

        try {
            repository.copy(listOf(source), source)
            fail("Expected copying a folder into itself to fail")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("itself"))
        }
    }

    @Test
    fun createsAndExtractsZipRoundTrip() = runBlocking {
        val source = repository.createFolder(root, "bundle-source")
        File(source, "readme.txt").writeText("zip body")
        File(source, "assets").mkdirs()
        File(source, "assets/icon.dat").writeBytes(ByteArray(1024) { 7 })

        val archive = repository.compress(
            sources = listOf(source),
            destination = root,
            archiveName = "bundle",
        )
        assertEquals("bundle.zip", archive.name)
        assertTrue(archive.length() > 0)

        val extracted = repository.extractZip(archive, root)
        assertEquals("bundle", extracted.name)
        assertEquals("zip body", File(extracted, "bundle-source/readme.txt").readText())
        assertEquals(1024, File(extracted, "bundle-source/assets/icon.dat").length())

        val secondArchive = repository.compress(
            sources = listOf(source),
            destination = root,
            archiveName = "bundle",
        )
        assertEquals("bundle (2).zip", secondArchive.name)
    }

    @Test
    fun rejectsZipSlipEntries() = runBlocking {
        val archive = File(root, "evil.zip")
        val escapedName = "escaped-${System.nanoTime()}.txt"
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../$escapedName"))
            zip.write("should not escape".toByteArray())
            zip.closeEntry()
        }

        try {
            repository.extractZip(archive, root)
            fail("Expected an unsafe archive path to be rejected")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("unsafe"))
        }
        assertFalse(File(root.parentFile, escapedName).exists())
        assertFalse(File(root, "evil").exists())
    }

    @Test
    fun deletingFolderDoesNotFollowSymlinks() = runBlocking {
        val target = File(root.parentFile, "shfe-link-target-${System.nanoTime()}")
        target.mkdirs()
        val kept = File(target, "keep.txt").apply { writeText("keep") }
        val folder = repository.createFolder(root, "folder-with-link")
        val link = File(folder, "link")
        val created = runCatching {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        }.isSuccess
        try {
            Assume.assumeTrue("Symbolic links are not supported on this filesystem", created)
            assertEquals(1, repository.delete(listOf(folder)))
            assertFalse(folder.exists())
            assertTrue(kept.exists())
            assertEquals("keep", kept.readText())
        } finally {
            if (created) link.delete()
            target.deleteRecursively()
        }
    }

    @Test
    fun searchHonorsFolderScopeAndStreamsMatches() = runBlocking {
        val nested = File(root, "nested").apply { mkdirs() }
        File(root, "match-root.txt").writeText("root")
        File(nested, "match-nested.txt").writeText("nested")
        File(root, "other.txt").writeText("other")

        val shallow = repository.search(
            root = root,
            query = "match",
            showHidden = false,
            subfolders = false,
        )
        assertEquals(listOf("match-root.txt"), shallow.map { it.name })

        val streamed = mutableListOf<String>()
        val deep = repository.search(
            root = root,
            query = "match",
            showHidden = false,
            subfolders = true,
            onMatch = { streamed.add(it.name) },
        )
        assertEquals(
            setOf("match-root.txt", "match-nested.txt"),
            deep.map { it.name }.toSet(),
        )
        assertEquals(deep.map { it.name }.toSet(), streamed.toSet())
    }

    @Test
    fun validatesFileNames() {
        assertEquals("normal.txt", repository.validateName(" normal.txt "))
        for (invalid in listOf("", "   ", ".", "..", "a/b", "a\u0000b")) {
            try {
                repository.validateName(invalid)
                fail("Expected '$invalid' to be rejected")
            } catch (_: IOException) {
                // Expected.
            }
        }
    }
}
