package com.vbutrim.index

import com.vbutrim.BE_CURIOUS_NOT_JUDGEMENTAL
import com.vbutrim.BE_CURIOUS_NOT_JUDGEMENTAL_TOKENS
import com.vbutrim.file.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
internal class DocumentsIndexerConcurrencyTest {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DocumentsIndexerConcurrencyTest::class.java)

        private const val testLaunchesNumber: Int = 100
        private const val junkFilesPerLaunchCount = 100
    }

    @Test
    fun shouldHandleMultipleRequests() = runTest {
        for (i in 1..testLaunchesNumber) {
            logger.info("Running $i launch")

            shouldHandleMultipleRequestSingleRun()
        }
    }

    private suspend fun shouldHandleMultipleRequestSingleRun() {
        withNewTempDirSuspendable { tempDir ->
            // Given
            val testContext = initTestContext(tempDir)

            // When
            val jobs = createTempFilesJobs(testContext)
            val resultD = createResultFileAndIndexJobAsync(testContext)

            jobs.joinAll()

            val result = resultD.await()

            // Then
            assertTempFileIsIndexed(testContext.documentsIndexer, result)
        }
    }

    private suspend fun initTestContext(tempDir: File): TestContext {
        return TestContext(
            tempDir,
            documentsIndexerWithTextsAndTempDir(tempDir),
            RandomStringGenerator(),
            Dispatchers.Default
        )
    }

    private suspend fun createTempFilesJobs(
        testContext: TestContext,
    ): List<Job> {
        val jobs = mutableListOf<Job>()
        for (i in 1..junkFilesPerLaunchCount) {
            jobs.add(
                createNewFileThenIndexThenRemoveJob(testContext)
            )
        }
        return jobs
    }

    private suspend fun createNewFileThenIndexThenRemoveJob(
        testContext: TestContext
    ) = coroutineScope {
        launch(testContext.coroutine) {
            val tempFile = createNewFile(testContext.tempDir, testContext.randomStringGenerator)

            addFileToIndexJob(testContext.coroutine, testContext.documentsIndexer, tempFile)
                .join()
            removeFileFromIndexJob(testContext.coroutine, testContext.documentsIndexer, tempFile)
                .join()

            tempFile.delete()
        }
    }

    private suspend fun createNewFile(
        tempDir: File,
        randomStringGenerator: RandomStringGenerator
    ): File {
        return createNewFile(tempDir, randomStringGenerator.nextString(), randomStringGenerator.nextString())
    }

    private suspend fun createNewFile(
        tempDir: File,
        fileName: String,
        content: String
    ): File {
        val tempFile = tempDir.child(fileName)

        withContext(Dispatchers.IO) {
            tempFile.createNewFile()
        }

        tempFile.writeText(content)
        return tempFile
    }

    /**
     * method of adding is randomly choosing between direct update or sync
     */
    private fun CoroutineScope.addFileToIndexJob(
        coroutineContext: CoroutineContext,
        documentsIndexer: DocumentsIndexer,
        tempFile: File
    ) = launch(coroutineContext) {
        documentsIndexer
            .updateWithAsync(listOf(tempFile.asAbsolutePath()), IndexedItemsFilter.ANY) {}
            .await()
    }

    /**
     * method of removing is randomly choosing between direct removes or sync
     */
    private fun CoroutineScope.removeFileFromIndexJob(
        coroutineContext: CoroutineContext,
        documentsIndexer: DocumentsIndexer,
        tempFile: File
    ) = launch(coroutineContext) {
        documentsIndexer
            .removeAsync(listOf(tempFile.asAbsolutePath()), listOf(), IndexedItemsFilter.ANY)
            .await()
    }

    private suspend fun createResultFileAndIndexJobAsync(
        testContext: TestContext
    ): Deferred<File> = coroutineScope {
        async(testContext.coroutine) {
            val tempFile = createNewFile(
                testContext.tempDir,
                testContext.randomStringGenerator.nextString(),
                BE_CURIOUS_NOT_JUDGEMENTAL
            )

            addFileToIndexJob(testContext.coroutine, testContext.documentsIndexer, tempFile)
                .join()

            return@async tempFile
        }
    }

    private suspend fun assertTempFileIsIndexed(documentsIndexer: DocumentsIndexer, tempFile: File) {
        val result = documentsIndexer
            .getDocumentThatContainTokenPathsAsync(BE_CURIOUS_NOT_JUDGEMENTAL_TOKENS)
            .await()
        Assertions.assertEquals(
            listOf(tempFile.asAbsolutePath()),
            result
        )
    }

    private suspend fun documentsIndexerWithTextsAndTempDir(tempDir: File): DocumentsIndexer {
        return documentsIndexerWithTextsSource().let {
            it.updateWithAsync(listOf(tempDir.asAbsolutePath()), IndexedItemsFilter.ANY) {}
                .await()
            it
        }
    }

    private data class TestContext(
        val tempDir: File,
        val documentsIndexer: DocumentsIndexer,
        val randomStringGenerator: RandomStringGenerator,
        val coroutine: CoroutineContext
    )
}