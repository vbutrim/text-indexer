package com.vbutrim.index.ui

import com.vbutrim.index.DocumentsIndexer
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path

suspend fun addDocumentsToIndex(
    documentPaths: List<Path>,
    updateResultsOnUI: suspend (List<Path>) -> Unit)
{
    coroutineScope {
        val indexedPaths = DocumentsIndexer.updateWith(documentPaths)
        updateResultsOnUI.invoke(indexedPaths)
    }
}