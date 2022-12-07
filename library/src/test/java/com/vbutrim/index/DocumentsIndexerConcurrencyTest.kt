package com.vbutrim.index

import com.vbutrim.BE_CURIOUS_NOT_JUDGEMENTAL
import com.vbutrim.file.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
internal class DocumentsIndexerConcurrencyTest {

    companion object {
        private const val testLaunchesNumber: Int = 100
        private const val junkFilesPerLaunchCount = 1_000
    }

    @Test
    fun shouldHandleMultipleRequests() = runTest {
        for (i in 1..testLaunchesNumber) {
            shouldHandleMultipleRequestSingleRun()
        }
    }

    private suspend fun shouldHandleMultipleRequestSingleRun() {
        withNewTempDirSuspendable { tempDir ->
            // Given
            val documentsIndexer = documentsIndexerWithTextsAndTempDir(tempDir)
            val randomStringGenerator = RandomStringGenerator()

            // When
            val jobs = createTempFilesJobs(
                tempDir,
                randomStringGenerator,
                documentsIndexer,
            )

            val resultD = createResultFileAndIndexJobAsync(
                Dispatchers.Default,
                tempDir,
                randomStringGenerator,
                documentsIndexer
            )

            jobs.joinAll()

            val result = resultD.await()

            // Then
            assertTempFileIsIndexed(documentsIndexer, result)
        }
    }

    private suspend fun assertTempFileIsIndexed(documentsIndexer: DocumentsIndexer, tempFile: File) {
        val result = documentsIndexer
            .getDocumentThatContainTokenPathsAsync(listOf("judgemental", "curious"))
            .await()
        Assertions.assertEquals(
            listOf(tempFile.asAbsolutePath()),
            result
        )
    }

    private suspend fun createTempFilesJobs(
        tempDir: File,
        randomStringGenerator: RandomStringGenerator,
        documentsIndexer: DocumentsIndexer,
    ): List<Job> {
        val jobs = mutableListOf<Job>()
        for (i in 1..junkFilesPerLaunchCount) {
            jobs.add(
                createNewFileThenIndexThenRemoveJob(
                    Dispatchers.Default,
                    tempDir,
                    randomStringGenerator,
                    documentsIndexer,
                )
            )
        }
        return jobs
    }

    private suspend fun createNewFileThenIndexThenRemoveJob(
        coroutineContext: CoroutineContext,
        tempDir: File,
        randomStringGenerator: RandomStringGenerator,
        documentsIndexer: DocumentsIndexer
    ) = coroutineScope {
        launch(coroutineContext) {
            val tempFile = createNewFile(tempDir, randomStringGenerator)

            addFileToIndexJob(coroutineContext, documentsIndexer, tempFile)
                .join()
            removeFileFromIndexJob(coroutineContext, documentsIndexer, tempFile)
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
        coroutineContext: CoroutineContext,
        tempDir: File,
        randomStringGenerator: RandomStringGenerator,
        documentsIndexer: DocumentsIndexer
    ): Deferred<File> = coroutineScope {
        async(coroutineContext) {
            val tempFile = createNewFile(
                tempDir,
                randomStringGenerator.nextString(),
                BE_CURIOUS_NOT_JUDGEMENTAL
            )

            addFileToIndexJob(coroutineContext, documentsIndexer, tempFile)
                .join()

            return@async tempFile
        }
    }

    private suspend fun documentsIndexerWithTextsAndTempDir(tempDir: File): DocumentsIndexer {
        return documentsIndexerWithTextsSource().let {
            it.updateWithAsync(listOf(tempDir.asAbsolutePath()), IndexedItemsFilter.ANY) {}
                .await()
            it
        }
    }
}