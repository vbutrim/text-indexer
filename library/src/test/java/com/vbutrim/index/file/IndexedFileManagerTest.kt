package com.vbutrim.index.file

import com.vbutrim.BE_CURIOUS_NOT_JUDGEMENTAL
import com.vbutrim.file.asAbsolutePath
import com.vbutrim.file.child
import com.vbutrim.file.readModificationTime
import com.vbutrim.file.withNewTempDirSuspendable
import com.vbutrim.index.IndexedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal class IndexedFileManagerTest {
    @Test
    fun shouldDefineOutdatedFileToSync() = runTest {
        withNewTempDirSuspendable { tempDir ->
            // Given
            val tempFiles = createTwoFiles(tempDir)
            val indexedItems = tempFiles.asIndexedItems()

            tempFiles.file1.writeText(BE_CURIOUS_NOT_JUDGEMENTAL)

            // When
            val result = IndexedFileManager.defineItemsToSync(indexedItems)

            // Then
            assertSingleFileToSync(result, tempFiles.file1)
        }
    }

    private data class Files(val tempDir: File, val file1: File, val file2: File) {
        fun asIndexedItems(): List<IndexedItem> {
            return listOf<IndexedItem>(
                IndexedItem.Dir(
                    tempDir.asAbsolutePath(),
                    listOf(
                        IndexedItem.File(
                            file1.asAbsolutePath(),
                            1,
                            file1.readModificationTime(),
                            true
                        ),
                        IndexedItem.File(
                            file2.asAbsolutePath(),
                            2,
                            file2.readModificationTime(),
                            false
                        )
                    )
                )
            )
        }
    }

    private fun assertSingleFileToSync(result: ToSync, tempFile1: File) {
        Assertions.assertEquals(1, result.filesToAdd.size)
        Assertions.assertEquals(tempFile1.asAbsolutePath(), result.filesToAdd.single().getPath())
        Assertions.assertTrue(result.toRemove.isEmpty())
    }

    private suspend fun createTwoFiles(tempDir: File): Files {
        val tempFile1 = tempDir.child("temp-file-1")
        val tempFile2 = tempDir.child("temp-file-2")

        withContext(Dispatchers.IO) {
            tempFile1.createNewFile()
            tempFile2.createNewFile()
        }

        return Files(tempDir, tempFile1, tempFile2)
    }

    private fun consIndexedItems(tempDir: File, tempFile1: File, tempFile2: File): List<IndexedItem> {
        return listOf<IndexedItem>(
            IndexedItem.Dir(
                tempDir.asAbsolutePath(),
                listOf(
                    IndexedItem.File(
                        tempFile1.asAbsolutePath(),
                        1,
                        tempFile1.readModificationTime(),
                        true
                    ),
                    IndexedItem.File(
                        tempFile2.asAbsolutePath(),
                        2,
                        tempFile2.readModificationTime(),
                        false
                    )
                )
            )
        )
    }
}