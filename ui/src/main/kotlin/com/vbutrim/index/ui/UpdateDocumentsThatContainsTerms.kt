package com.vbutrim.index.ui

import com.vbutrim.index.DocumentsIndexer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Path

suspend fun updateDocumentThatContainsTerms(
    tokens: List<String>,
    updateResultsOnUI: suspend (List<Path>) -> Unit)
{
    coroutineScope {
        launch {
            val documents = DocumentsIndexer.getDocumentThatContainTokenPaths(tokens)
            updateResultsOnUI.invoke(documents)
        }
    }
}