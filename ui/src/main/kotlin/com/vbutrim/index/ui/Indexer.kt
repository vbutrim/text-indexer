package com.vbutrim.index.ui

import com.vbutrim.coroutine.executePeriodically
import com.vbutrim.file.AbsolutePath
import com.vbutrim.index.DocumentsIndexer
import com.vbutrim.index.IndexedItem
import com.vbutrim.index.IndexedItemsFilter
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

interface Indexer : CoroutineScope {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Indexer::class.java)
    }

    val job: Job
    val documentsIndexer: DocumentsIndexer

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
        addRemoveDocumentsToIndexListener {
            removeDocumentsToIndex()
        }
        addUserSelectionOnlyListener {
            updateIndexedDocuments()
        }
        addSyncIndexedDocumentsListener {
            launch(Dispatchers.Default) {
                syncIndexedDocuments()
            }
        }

        launch(Dispatchers.Default) {
            executePeriodically(syncDelayTime()) {
                syncIndexedDocuments()
            }
        }
    }

    fun setStatus(text: String, iconRunning: Boolean)

    fun setActionStatus(nextActionIsEnabled: Boolean)

    /**
     * search
     */
    fun addSearchListener(listener: () -> Unit)

    private fun updateDocumentsThatContainTerms() {
        val tokens = getTokensToSearch()

        if (tokens.isEmpty()) {
            updateDocumentsThatContainTerms(listOf())
            return
        }

        setActionStatus(nextActionIsEnabled = false)
        updateDocumentsThatContainTerms(listOf())
        updateStatus(Status.SEARCH_IN_PROGRESS)

        val startTime = System.currentTimeMillis()
        launch(Dispatchers.Default) {
            val documents = documentsIndexer
                .getDocumentThatContainTokenPathsAsync(tokens)
                .await()

            withContext(Dispatchers.Main) {
                if (documents.isNotEmpty()) {
                    updateDocumentsThatContainTerms(documents)
                }

                updateStatus(Status.SEARCH_COMPLETED, startTime)
                setActionStatus(nextActionIsEnabled = true)
            }
        }
    }

    private fun updateStatus(
        status: Status,
        startTime: Long? = null
    ) {
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
            Status.INDEX_IN_PROGRESS -> true
        }

        setStatus(text, iconRunning)
    }

    fun getTokensToSearch(): List<String>

    fun updateDocumentsThatContainTerms(documents: List<AbsolutePath>)

    /**
     * close window
     */
    fun addOnWindowClosingListener(listener: () -> Unit)

    /**
     * documents to index
     */
    fun addGetDocumentsToIndexListener(listener: () -> Unit)

    private fun addDocumentsToIndex() {
        val pathsToIndex = getDocumentsToIndex()

        if (pathsToIndex.isEmpty()) {
            log.debug("Nothing has been chosen to index")
            return
        }

        log.debug(String.format("Going to update indexer with %s paths: %s", pathsToIndex.size, pathsToIndex))

        setActionStatus(nextActionIsEnabled = false)
        updateStatus(Status.INDEX_IN_PROGRESS)

        val startTime = System.currentTimeMillis()
        launch(Dispatchers.Default) {

            val updated = documentsIndexer
                .updateWithAsync(pathsToIndex, indexedItemsFilter()) {
                    withContext(Dispatchers.Main) {
                        updateIndexedDocuments(it)
                    }
                }
                .await()

            withContext(Dispatchers.Main) {
                when (updated) {
                    is DocumentsIndexer.Updated.Nothing -> {}
                    is DocumentsIndexer.Updated.Some -> updateIndexedDocuments(updated.finalIndexedItems)
                }
                updateStatus(Status.INDEX_COMPLETED, startTime)
                setActionStatus(nextActionIsEnabled = true)
            }
        }
    }

    fun getDocumentsToIndex(): List<AbsolutePath>

    fun updateIndexedDocuments(documents: List<IndexedItem>)

    fun addUserSelectionOnlyListener(listener: () -> Unit)

    private fun updateIndexedDocuments() {
        setActionStatus(nextActionIsEnabled = false)

        launch(Dispatchers.Default) {
            val indexedDocuments = documentsIndexer.getIndexedItems(indexedItemsFilter())

            withContext(Dispatchers.Main) {
                updateIndexedDocuments(indexedDocuments)
                setActionStatus(nextActionIsEnabled = true)
            }
        }
    }

    fun indexedItemsFilter(): IndexedItemsFilter

    fun addRemoveDocumentsToIndexListener(listener: () -> Unit)

    fun getDocumentsToIndexToRemove(): ToRemove

    private fun removeDocumentsToIndex() {
        val toRemove = getDocumentsToIndexToRemove()

        if (toRemove.isEmpty()) {
            return
        }

        setActionStatus(nextActionIsEnabled = false)
        updateStatus(Status.INDEX_IN_PROGRESS)

        launch(Dispatchers.Default) {

            val removed = documentsIndexer
                .removeAsync(toRemove.files, toRemove.dirs, indexedItemsFilter())
                .await()

            withContext(Dispatchers.Main) {
                when (removed) {
                    is DocumentsIndexer.Removed.Nothing -> {}
                    is DocumentsIndexer.Removed.Some -> updateIndexedDocuments(removed.finalIndexedItems)
                }

                updateStatus(Status.INDEX_COMPLETED)
                setActionStatus(nextActionIsEnabled = true)
                updateDocumentsThatContainTerms()
            }
        }
    }

    data class ToRemove(val files: List<AbsolutePath>, val dirs: List<AbsolutePath>) {
        fun isEmpty(): Boolean {
            return files.isEmpty() && dirs.isEmpty()
        }
    }

    /**
     * Sync
     */
    fun addSyncIndexedDocumentsListener(listener: () -> Unit)

    suspend fun syncIndexedDocuments() = coroutineScope {
        log.debug("Syncing indexed documents")

        withContext(Dispatchers.Main) {
            setActionStatus(nextActionIsEnabled = false)
            updateStatus(Status.INDEX_IN_PROGRESS)
        }

        val synced = documentsIndexer
            .syncIndexedItemsAsync(indexedItemsFilter())
            .await()

        withContext(Dispatchers.Main) {
            when (synced) {
                is DocumentsIndexer.Synced.Nothing -> {}
                is DocumentsIndexer.Synced.Some -> {
                    updateIndexedDocuments(synced.finalIndexedItems)
                    updateDocumentsThatContainTerms()
                }
            }

            setActionStatus(nextActionIsEnabled = true)
            updateStatus(Status.INDEX_COMPLETED)
        }
    }

    fun syncDelayTime(): Duration

    private enum class Status {
        IDLE,
        SEARCH_COMPLETED,
        SEARCH_IN_PROGRESS,
        INDEX_COMPLETED,
        INDEX_IN_PROGRESS
    }
}