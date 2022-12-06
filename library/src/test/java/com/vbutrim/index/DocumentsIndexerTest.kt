package com.vbutrim.index

import com.vbutrim.*
import com.vbutrim.file.AbsolutePath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

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