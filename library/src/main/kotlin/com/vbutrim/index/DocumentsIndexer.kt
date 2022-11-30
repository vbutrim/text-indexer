package com.vbutrim.index

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.*

object DocumentsIndexer {
    private val documentTokenizer: DocumentTokenizer = DocumentTokenizer.BasedOnWordSeparation()
    private val mutex: Mutex = Mutex();

    suspend fun getDocumentThatContainTokenPaths(tokens: List<String>): List<Path> {
        if (tokens.isEmpty()) {
            return listOf()
        }
        mutex.withLock {
            return tokens
                .asSequence()
                .map { Index.getDocumentThatContainTokenIds(it) }
                .reduce { acc, it -> acc.intersect(it) }
                .mapNotNull { IndexedDocuments.getFileById(it) }
                .map { it.path }
                .sorted()
                .toList()
        }
    }

    suspend fun updateWith(path: Path) {
        updateWith(listOf(path))
    }

    /**
     * @return all indexed paths
     */
    suspend fun updateWith(paths: List<Path>): List<Path> {
        mutex.withLock {
            coroutineScope {
                val actor = indexerActor()

                val tasks = paths.map {
                    launch {
                        actor.send(RemoveIfPresentDocumentMsg(it))

                        val document: Document.Tokenized = documentTokenizer.tokenize(DocumentReader.read(it))

                        actor.send(AddDocumentMsg(document))
                    }
                }

                tasks.joinAll()
                actor.close()
            }

            return paths
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun CoroutineScope.indexerActor() = actor<IndexerMsg> {
        for (msg in channel) {
            when (msg) {
                is RemoveIfPresentDocumentMsg -> {
                    val existing: IndexedDocuments.File? = IndexedDocuments.getFileByPath(msg.path)

                    if (existing != null) {
                        Index.remove(existing.id)
                    }
                }
                is AddDocumentMsg -> {
                    val documentId = IndexedDocuments.addDocument(msg.document).id
                    Index.updateWith(msg.document, documentId)
                }
            }
        }
    }

    sealed class IndexerMsg
    class RemoveIfPresentDocumentMsg(val path: Path) : IndexerMsg()
    class AddDocumentMsg(val document: Document.Tokenized) : IndexerMsg()
}