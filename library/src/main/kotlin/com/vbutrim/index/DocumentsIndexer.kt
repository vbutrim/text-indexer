package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FileManager
import com.vbutrim.file.FilesAndDirs
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

object DocumentsIndexer {
    private val documentTokenizer: DocumentTokenizer = DocumentTokenizer.BasedOnWordSeparation()
    private val mutex: Mutex = Mutex()

    suspend fun getDocumentThatContainTokenPaths(tokens: List<String>): List<AbsolutePath> {
        if (tokens.isEmpty()) {
            return listOf()
        }

        mutex.withLock {
            return tokens
                .asSequence()
                .map { Index.getDocumentThatContainTokenIds(it) }
                .reduce { acc, it -> acc.intersect(it) }
                .mapNotNull { IndexedDocuments.getFileById(it)?.path }
                .sortedBy { it.asPath() }
                .toList()
        }
    }

    /**
     * @return all indexed paths
     */
    suspend fun updateWithAsync(paths: List<AbsolutePath>): Deferred<List<IndexedDocuments.Item>> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || paths.isEmpty()) {
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
        indexerActor: SendChannel<IndexerMessage>,
        file: FilesAndDirs.File
    ) = launch {
        indexerActor.send(IndexerMessage.RemoveDocumentIfPresent(file))

        val document: Document.Tokenized = documentTokenizer.tokenize(DocumentReader.read(file))

        indexerActor.send(IndexerMessage.AddDocument(document, file.isNestedWithDir))
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun CoroutineScope.indexerActor() = actor<IndexerMessage> {
        for (msg in channel) {
            when (msg) {
                is IndexerMessage.RemoveDocumentIfPresent -> {
                    val existing: IndexedDocuments.File? = IndexedDocuments.getFileByPath(msg.file.getPath())

                    if (existing != null) {
                        Index.remove(existing.id)
                    }
                }

                is IndexerMessage.AddDocument -> {
                    val documentId = IndexedDocuments.add(msg.document, msg.fileIsNestedWithDir).id
                    Index.updateWith(msg.document, documentId)
                }
            }
        }
    }

    private sealed class IndexerMessage {
        class RemoveDocumentIfPresent(val file: FilesAndDirs.File) : IndexerMessage()
        class AddDocument(val document: Document.Tokenized, val fileIsNestedWithDir: Boolean) : IndexerMessage()
    }

    /**
     * @return all indexed paths
     */
    suspend fun removeAsync(paths: List<AbsolutePath>): Deferred<List<IndexedDocuments.Item>> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || paths.isEmpty()) {
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
}