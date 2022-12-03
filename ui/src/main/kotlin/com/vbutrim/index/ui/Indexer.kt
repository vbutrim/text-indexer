package com.vbutrim.index.ui

import com.vbutrim.index.DocumentsIndexer
import com.vbutrim.index.IndexedDocuments
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

interface Indexer : CoroutineScope {

    val job: Job;

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    fun init() {
        addOnWindowClosingListener {
            job.cancel()
            exitProcess(0)
        }
        addSearchListener {
            updateDocumentsThatContainTerms()
        }
        addGetDocumentsToIndexListener {
            addDocumentsToIndex()
        }
    }

    fun setStatus(text: String, iconRunning: Boolean)

    fun setActionsStatus(newSearchIsEnabled: Boolean, newIndexingIsEnabled: Boolean)

    /**
     * search
     */
    fun addSearchListener(listener: () -> Unit)

    fun updateDocumentsThatContainTerms() {
        val tokens = getTokensToSearch()

        setActionsStatus(newSearchIsEnabled = false, newIndexingIsEnabled = false)
        updateDocumentsThatContainTerms(listOf())
        updateStatus(Status.SEARCH_IN_PROGRESS)

        val startTime = System.currentTimeMillis()
        launch(Dispatchers.Default) {
            val documents = DocumentsIndexer.getDocumentThatContainTokenPaths(tokens)

            withContext(Dispatchers.Main) {
                updateDocumentsThatContainTerms(documents)
                updateStatus(Status.SEARCH_COMPLETED, startTime)
                setActionsStatus(newSearchIsEnabled = true, newIndexingIsEnabled = true)
            }
        }
    }

    private fun updateStatus(
        status: Status,
        startTime: Long? = null)
    {
        val time = if (startTime != null) {
            val time = System.currentTimeMillis() - startTime
            " in ${(time / 1000)}.${time % 1000 / 100} sec"
        } else ""

        val text = when (status) {
            Status.IDLE -> ""
            Status.SEARCH_COMPLETED -> "Searching: completed$time"
            Status.SEARCH_IN_PROGRESS -> "Searching: in progress$time"
            Status.INDEX_COMPLETED -> "Indexing: completed$time"
            Status.INDEX_IN_PROGRESS -> "Indexing: in progress$time"
        }

        val iconRunning = when (status) {
            Status.IDLE -> false
            Status.SEARCH_COMPLETED -> false
            Status.SEARCH_IN_PROGRESS -> true
            Status.INDEX_COMPLETED -> false
            Status.INDEX_IN_PROGRESS -> false
        }

        setStatus(text, iconRunning)
    }

    fun getTokensToSearch(): List<String>

    fun updateDocumentsThatContainTerms(documents: List<Path>)

    /**
     * close window
     */
    fun addOnWindowClosingListener(listener: () -> Unit)

    /**
     * documents to index
     */
    fun addGetDocumentsToIndexListener(listener: () -> Unit)

    fun addDocumentsToIndex() {
        val pathsToIndex = getDocumentsToIndex()

        if (pathsToIndex.isEmpty()) {
            return
        }

        setActionsStatus(newSearchIsEnabled = false, newIndexingIsEnabled = false)
        updateStatus(Status.INDEX_IN_PROGRESS)

        val startTime = System.currentTimeMillis()
        launch(Dispatchers.Default) {
            val indexedDocuments = DocumentsIndexer.updateWithAsync(pathsToIndex)
                .await()

            withContext(Dispatchers.Main) {
                updateIndexedDocuments(indexedDocuments)
                updateStatus(Status.INDEX_COMPLETED, startTime)
                setActionsStatus(newSearchIsEnabled = true, newIndexingIsEnabled = true)
            }
        }
    }

    fun getDocumentsToIndex(): List<Path>

    fun updateIndexedDocuments(documents: List<IndexedDocuments.Item>)

    private enum class Status {
        IDLE,
        SEARCH_COMPLETED,
        SEARCH_IN_PROGRESS,
        INDEX_COMPLETED,
        INDEX_IN_PROGRESS
    }
}