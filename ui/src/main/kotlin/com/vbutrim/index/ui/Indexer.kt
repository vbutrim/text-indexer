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
        addRemoveIndexedDocumentsListener {
            removeIndexedDocuments()
        }
        addUserSelectionOnlyListener {
            updateIndexedDocuments()
        }
        addSyncIndexedDocumentsListener {
            launch(Dispatchers.Default) {
                syncIndexedDocuments(false)
            }
        }

        launch(Dispatchers.Default) {
            executePeriodically(syncDelayTime()) {
                syncIndexedDocuments(true)
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
            Status.SOMETHING_GOES_WRONG -> "Something goes wrong"
        }

        val iconRunning = when (status) {
            Status.IDLE -> false
            Status.SEARCH_COMPLETED -> false
            Status.SEARCH_IN_PROGRESS -> true
            Status.INDEX_COMPLETED -> false
            Status.INDEX_IN_PROGRESS -> true
            Status.SOMETHING_GOES_WRONG -> false
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
        try {
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
                    updateIndexedDocumentsAndEnableNextActions(updated, startTime)
                }
            }
        } catch (exception: Exception) {
            showErrorStatusAndEnableNextAction(exception)
        }
    }

    private fun showErrorStatusAndEnableNextAction(exception: Exception) {
        log.error("Something goes wrong", exception)
        updateStatus(Status.SOMETHING_GOES_WRONG)
        setActionStatus(nextActionIsEnabled = true)
    }

    private fun updateIndexedDocumentsAndEnableNextActions(
        result: DocumentsIndexer.Result,
        startTime: Long? = null,
        onSomeResult: (() -> Unit)? = null
    ) {
        when (result) {
            is DocumentsIndexer.Result.Nothing -> {}
            is DocumentsIndexer.Result.Some -> {
                updateIndexedDocuments(result.finalIndexedItems)
                onSomeResult?.invoke()
            }
        }
        updateStatus(Status.INDEX_COMPLETED, startTime)
        setActionStatus(nextActionIsEnabled = true)
    }

    fun getDocumentsToIndex(): List<AbsolutePath>

    fun updateIndexedDocuments(documents: List<IndexedItem>)

    fun addUserSelectionOnlyListener(listener: () -> Unit)

    private fun updateIndexedDocuments() {
        try {
            setActionStatus(nextActionIsEnabled = false)

            launch(Dispatchers.Default) {
                val indexedDocuments = documentsIndexer.getIndexedItems(indexedItemsFilter())

                withContext(Dispatchers.Main) {
                    updateIndexedDocuments(indexedDocuments)
                    setActionStatus(nextActionIsEnabled = true)
                }
            }
        } catch(exception: Exception) {
            showErrorStatusAndEnableNextAction(exception)
        }
    }

    fun indexedItemsFilter(): IndexedItemsFilter

    fun addRemoveIndexedDocumentsListener(listener: () -> Unit)

    fun getIndexedDocumentsToRemove(): ToRemove

    private fun removeIndexedDocuments() {
        try {
            val toRemove = getIndexedDocumentsToRemove()

            if (toRemove.isEmpty()) {
                return
            }

            setActionStatus(nextActionIsEnabled = false)
            updateStatus(Status.INDEX_IN_PROGRESS)

            val startTime = System.currentTimeMillis()
            launch(Dispatchers.Default) {
                val removed = documentsIndexer
                    .removeAsync(toRemove.files, toRemove.dirs, indexedItemsFilter())
                    .await()

                withContext(Dispatchers.Main) {
                    updateIndexedDocumentsAndEnableNextActions(removed, startTime) {
                        updateDocumentsThatContainTerms()
                    }
                }
            }
        } catch (exception: Exception) {
            showErrorStatusAndEnableNextAction(exception)
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

    /**
     * @param nextActionIsEnabledDuringSync not to freeze UI during sync in the background. As there is mutex, which defines
     * critical section on each external method, there wouldn't be any concurrency issue
     */
    suspend fun syncIndexedDocuments(nextActionIsEnabledDuringSync: Boolean) = coroutineScope {
        try {
            log.debug("Syncing indexed documents")

            withContext(Dispatchers.Main) {
                setActionStatus(nextActionIsEnabledDuringSync)
                updateStatus(Status.INDEX_IN_PROGRESS)
            }

            val synced = documentsIndexer
                .syncIndexedItemsAsync(indexedItemsFilter())
                .await()

            withContext(Dispatchers.Main) {
                updateIndexedDocumentsAndEnableNextActions(synced) {
                    updateDocumentsThatContainTerms()
                }
            }
        } catch (exception: Exception) {
            withContext(Dispatchers.Main) {
                showErrorStatusAndEnableNextAction(exception)
            }
        }
    }

    fun syncDelayTime(): Duration

    private enum class Status {
        IDLE,
        SEARCH_COMPLETED,
        SEARCH_IN_PROGRESS,
        INDEX_COMPLETED,
        INDEX_IN_PROGRESS,
        SOMETHING_GOES_WRONG
    }
}