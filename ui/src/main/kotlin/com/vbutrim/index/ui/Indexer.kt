package com.vbutrim.index.ui

import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

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
            System.exit(0)
        }
    }

    fun updateDocumentsThatContainsTerms() {
        val tokens = getTokensToSearch()
        clearResults()

        launch(Dispatchers.Default) {
            updateDocumentThatContainsTerms(tokens) { documents, completed ->
                withContext(Dispatchers.Main) {
                    updateDocumentsThatContainsTerms(documents)
                }
            }
        }
    }

    private fun clearResults() {
        updateDocumentsThatContainsTerms(listOf())
        // updateLoadingStatus(IN_PROGRESS)
        // setActionsStatus(newLoadingEnabled = false)
    }

    fun addSearchListener(listener: () -> Unit)

    fun updateDocumentsThatContainsTerms(documents: List<Path>)

    fun addOnWindowClosingListener(listener: () -> Unit)

    fun getTokensToSearch(): List<String>
}