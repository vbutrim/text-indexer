package com.vbutrim.index

import com.vbutrim.file.FileManager
import com.vbutrim.file.FilesAndDirs
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
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

    /**
     * @return all indexed paths
     */
    suspend fun updateWithAsync(paths: List<Path>): Deferred<List<IndexedDocuments.Item>> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (paths.isEmpty()) {
                        return@async IndexedDocuments.getAllIndexedPaths()
                    }

                    val filesAndFolders = FileManager.splitOnFilesAndDirs(paths)
                    filesAndFolders.dirs.forEach { IndexedDocuments.add(it) }

                    val actor = indexerActor()

                    filesAndFolders
                        .getAllFilesUnique()
                        .map { consJobToIndexFileAndRun(actor, it) }
                        .joinAll()

                    actor.close()

                    return@async IndexedDocuments.getAllIndexedPaths()
                }
            }
        }

    private fun CoroutineScope.consJobToIndexFileAndRun(
        indexerActor: SendChannel<IndexerMsg>,
        file: FilesAndDirs.File
    ) = launch {
        indexerActor.send(RemoveIfPresentDocumentMsg(file))

        val document: Document.Tokenized = documentTokenizer.tokenize(DocumentReader.read(file))

        indexerActor.send(AddDocumentMsg(document))
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun CoroutineScope.indexerActor() = actor<IndexerMsg> {
        for (msg in channel) {
            when (msg) {
                is RemoveIfPresentDocumentMsg -> {
                    val existing: IndexedDocuments.File? = IndexedDocuments.getFileByPath(msg.file.getPath())

                    if (existing != null) {
                        Index.remove(existing.id)
                    }
                }

                is AddDocumentMsg -> {
                    val documentId = IndexedDocuments.add(msg.document).id
                    Index.updateWith(msg.document, documentId)
                }
            }
        }
    }

    sealed class IndexerMsg
    class RemoveIfPresentDocumentMsg(val file: FilesAndDirs.File) : IndexerMsg()
    class AddDocumentMsg(val document: Document.Tokenized) : IndexerMsg()
}