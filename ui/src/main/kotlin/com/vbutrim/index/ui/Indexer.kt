package com.vbutrim.index.ui

import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

interface Indexer : CoroutineScope {

    val job: Job;

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    fun init() {
        addSearchListener {
            updateDocumentsThatContainsTerms()
        }
        addOnWindowClosingListener {
            job.cancel()
            exitProcess(0)
        }
    }

    private enum class Status { IDLE, SEARCH_COMPLETED, SEARCH_IN_PROGRESS, INDEX_COMPLETED, INDEX_IN_PROGRESS }

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

    fun setStatus(text: String, iconRunning: Boolean)

    fun updateDocumentsThatContainsTerms() {
        val tokens = getTokensToSearch()
        clearResults()

        val startTime = System.currentTimeMillis()
        launch(Dispatchers.Default) {
            updateDocumentThatContainsTerms(tokens) { documents ->
                withContext(Dispatchers.Main) {
                    updateDocumentsThatContainsTerms(documents)
                    updateStatus(Status.SEARCH_COMPLETED, startTime)
                }
            }
        }
    }

    private fun clearResults() {
        updateDocumentsThatContainsTerms(listOf())
        updateStatus(Status.SEARCH_IN_PROGRESS)
    }

    fun addSearchListener(listener: () -> Unit)

    fun updateDocumentsThatContainsTerms(documents: List<Path>)

    fun addOnWindowClosingListener(listener: () -> Unit)

    fun getTokensToSearch(): List<String>
}