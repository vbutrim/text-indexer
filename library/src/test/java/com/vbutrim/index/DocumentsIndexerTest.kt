package com.vbutrim.index

import com.vbutrim.*
import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.asAbsolutePath
import com.vbutrim.file.child
import com.vbutrim.file.withNewTempDirSuspendable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

@ExperimentalCoroutinesApi
internal class DocumentsIndexerTest {
    @Test
    fun shouldUpdateIndexWithAsync() = runTest {
        // Given
        val documentsIndexer = documentsIndexer()

        // When
        val result = documentsIndexer
            .updateWithAsync(listOf(textsDirectoryAbsolutePath), IndexedItemsFilter.ANY) {}
            .await()

        // Then
        assertTextsDirIsFullyIndexed(result)
        assertSimpleSearchResultIsCorrect(documentsIndexer)
    }

    private fun assertTextsDirIsFullyIndexed(result: DocumentsIndexer.Result) {
        Assertions.assertTrue(result is DocumentsIndexer.Result.Some)

        val finalIndexedItems = (result as DocumentsIndexer.Result.Some).finalIndexedItems

        assertTextsDirIsFullyIndexed(finalIndexedItems)
    }

    private fun assertTextsDirIsFullyIndexed(finalIndexedItems: List<IndexedItem>) {
        Assertions.assertEquals(
            listOf(textsDirectoryAbsolutePath),
            finalIndexedItems.map { it.path }
        )
        Assertions.assertEquals(
            listOf(fightClubAbsolutePath, practicalGuideToHappinessAbsolutePath, starWarsAbsolutePath),
            finalIndexedItems.flatMappedFiles().map { it.path }
        )
    }

    private suspend fun assertSimpleSearchResultIsCorrect(documentsIndexer: DocumentsIndexer) {
        val searchResult = documentsIndexer
            .getDocumentThatContainTokenPathsAsync(listOf("me", "i", "tyler"))
            .await()
        Assertions.assertEquals(
            listOf(fightClubAbsolutePath),
            searchResult.map { it }
        )
    }

    @Test
    fun shouldGetAllIndexedItems() = runTest {
        // Given
        val documentsIndexer = documentsIndexerWithTextsSource()

        // When
        val result = documentsIndexer.getIndexedItems(IndexedItemsFilter.ANY)

        // Then
        assertTextsDirIsFullyIndexed(result)
        assertSimpleSearchResultIsCorrect(documentsIndexer)
    }

    @Test
    fun shouldGetSourceOnlyIndexedItems() = runTest {
        // Given
        val documentsIndexer = documentsIndexerWithTextsSource()

        // When
        val result = documentsIndexer.getIndexedItems(IndexedItemsFilter.SOURCES_ONLY)

        // Then
        Assertions.assertEquals(
            listOf(textsDirectoryAbsolutePath),
            result.map { it.path }
        )
        Assertions.assertTrue(result.flatMappedFiles().isEmpty())
        assertSimpleSearchResultIsCorrect(documentsIndexer)
    }

    @Test
    fun shouldRemoveFileAsync() = runTest {
        // Given
        val documentsIndexer = documentsIndexerWithTextsSource()

        // When
        val result = documentsIndexer
            .removeAsync(
                listOf(fightClubAbsolutePath),
                listOf(),
                IndexedItemsFilter.ANY
            )
            .await()

        // Then
        assertTextsDirContainsFiles(result, fightClubIsPresent = false)
    }

    @Test
    fun shouldRemoveDirAsync() = runTest {
        // Given
        val documentsIndexer = documentsIndexerWithTextsSource()

        // When
        val result = documentsIndexer
            .removeAsync(
                listOf(),
                listOf(textsMarkMansonDirectoryAbsolutePath),
                IndexedItemsFilter.ANY
            )
            .await()

        // Then
        assertTextsDirContainsFiles(result, practicalGuideToHappinessIsPresent = false)
    }

    private fun assertTextsDirContainsFiles(
        result: DocumentsIndexer.Result,
        fightClubIsPresent: Boolean = true,
        practicalGuideToHappinessIsPresent: Boolean = true)
    {
        Assertions.assertTrue(result is DocumentsIndexer.Result.Some)

        val finalIndexedItems = (result as DocumentsIndexer.Result.Some).finalIndexedItems

        Assertions.assertEquals(
            mutableListOf<AbsolutePath>().let {
                if (fightClubIsPresent) {
                    it.add(fightClubAbsolutePath)
                }
                if (practicalGuideToHappinessIsPresent) {
                    it.add(practicalGuideToHappinessAbsolutePath)
                }
                it.add(starWarsAbsolutePath)
                it.toList()
            },
            finalIndexedItems.flatMappedFiles().map { it.path }
        )
    }

    @Test
    fun shouldSyncNewFileAsync() = runTest {
        withNewTempDirSuspendable { tempDir ->
            // Given
            val documentsIndexer = documentsIndexer()

            documentsIndexer
                .updateWithAsync(listOf(tempDir.asAbsolutePath()), IndexedItemsFilter.ANY) {}
                .await()

            val tempFile = tempDir.child("temp_file")
            withContext(Dispatchers.IO) {
                tempFile.createNewFile()
            }

            tempFile.writeText("Be curious, not judgemental")

            // When
            val result = documentsIndexer
                .syncIndexedItemsAsync(IndexedItemsFilter.ANY) {}
                .await()

            // Then
            assertResultContainsTempDirOnly(result, tempDir, tempFile)
            assertTempFileIsIndexed(documentsIndexer, tempFile)
        }
    }

    @Test
    fun shouldSyncRemovedFileAsync() = runTest {
        withNewTempDirSuspendable { tempDir ->
            // Given
            val documentsIndexer = documentsIndexer()

            val tempFile = tempDir.child("temp_file")
            withContext(Dispatchers.IO) {
                tempFile.createNewFile()
            }

            tempFile.writeText("Be curious, not judgemental")


            documentsIndexer
                .updateWithAsync(listOf(tempDir.asAbsolutePath()), IndexedItemsFilter.ANY) {}
                .await()

            assertTempFileIsIndexed(documentsIndexer, tempFile)

            tempFile.delete()

            // When
            val result = documentsIndexer
                .syncIndexedItemsAsync(IndexedItemsFilter.ANY) {}
                .await()

            // Then
            assertResultContainsTempDirOnly(result, tempDir, null)
            assertTempFileIsNotIndexed(documentsIndexer, tempFile)
        }
    }

    private fun assertResultContainsTempDirOnly(
        result: DocumentsIndexer.Result,
        tempDir: File,
        tempFile: File?
    ) {
        Assertions.assertTrue(result is DocumentsIndexer.Result.Some)

        val finalIndexedItems = (result as DocumentsIndexer.Result.Some).finalIndexedItems

        Assertions.assertEquals(
            listOf(tempDir.asAbsolutePath()),
            finalIndexedItems.map { it.path }
        )

        if (tempFile != null) {
            Assertions.assertEquals(
                listOf(tempFile.asAbsolutePath()),
                finalIndexedItems.flatMappedFiles().map { it.path }
            )
        } else {
            Assertions.assertTrue(finalIndexedItems.flatMappedFiles().map { it.path }.isEmpty())
        }
    }

    private suspend fun assertTempFileIsIndexed(documentsIndexer: DocumentsIndexer, tempFile: File) {
        Assertions.assertEquals(
            listOf(tempFile.asAbsolutePath()),
            documentsIndexer
                .getDocumentThatContainTokenPathsAsync(listOf("judgemental", "curious"))
                .await()
        )
    }

    private suspend fun assertTempFileIsNotIndexed(documentsIndexer: DocumentsIndexer, tempFile: File) {
        Assertions.assertTrue(
            documentsIndexer
                .getDocumentThatContainTokenPathsAsync(listOf("judgemental", "curious"))
                .await()
                .isEmpty()
        )
    }
}

fun documentsIndexer(): DocumentsIndexer {
    return DocumentsIndexer(
        DocumentTokenizer.BasedOnWordSeparation()
    )
}

suspend fun documentsIndexerWithTextsSource(): DocumentsIndexer {
    return documentsIndexer().let {
        it.updateWithAsync(listOf(textsDirectoryAbsolutePath), IndexedItemsFilter.ANY) {}
            .await()
        it
    }
}