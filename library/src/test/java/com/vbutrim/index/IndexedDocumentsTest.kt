package com.vbutrim.index

import com.vbutrim.BE_CURIOUS_NOT_JUDGEMENTAL
import com.vbutrim.file.FilesAndDirs
import com.vbutrim.file.child
import com.vbutrim.file.readModificationTime
import com.vbutrim.file.withNewTempDir
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant

internal class IndexedDocumentsTest {

    @Test
    fun shouldUpdateModificationTimeOnAddFile() {
        withNewTempDir { tempDir ->
            // Given
            val testContext = initTestContext(tempDir)

            testContext.tempFile.writeText("\nWalt Whitman")

            val expected = testContext.tempFile.readModificationTime()

            // When
            val result = indexFile(testContext)
                .getModificationTime()

            // Then
            assertNotEquals(testContext.oldModificationTime, result)
            assertEquals(expected, result)
        }
    }

    private fun initTestContext(tempDir: File): TestContext {
        val documentsTokenizer = DocumentTokenizer.BasedOnWordSeparation()
        val indexedDocuments = IndexedDocuments()
        val tempFile = createTempFile(tempDir)
        val oldModificationTime = tempFile.readModificationTime()

        val testContext = TestContext(
            documentsTokenizer,
            indexedDocuments,
            tempFile,
            tempFile.readModificationTime()
        )

        indexFile(testContext)

        assertEquals(tempFile.readModificationTime(), oldModificationTime)

        return testContext
    }

    private fun createTempFile(tempDir: File): File {
        val tempFile = tempDir.child("temp_file")
        tempFile.createNewFile()

        tempFile.writeText(BE_CURIOUS_NOT_JUDGEMENTAL)
        return tempFile
    }

    private fun indexFile(
        testContext: TestContext
    ): IndexedItem.File {
        val document = DocumentReader.read(FilesAndDirs.File.independentSource(testContext.tempFile))
        return testContext.indexedDocuments.add(
            testContext.documentsTokenizer.tokenize(document),
            false
        )
    }

    private fun assertNotEquals(unexpected: Instant, result: Instant) {
        Assertions.assertNotEquals(unexpected, result)
    }

    private fun assertEquals(expected: Instant, result: Instant) {
        Assertions.assertEquals(expected, result)
    }

    private class TestContext(
        val documentsTokenizer: DocumentTokenizer,
        val indexedDocuments: IndexedDocuments,
        val tempFile: File,
        val oldModificationTime: Instant
    )
}