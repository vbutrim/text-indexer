package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FileManager
import com.vbutrim.file.FilesAndDirs
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import java.util.*

private val log: Logger = org.slf4j.LoggerFactory.getLogger(DocumentsIndexer::class.java)

class DocumentsIndexer(
    private val documentTokenizer: DocumentTokenizer)
{
    private val indexedDocuments = IndexedDocuments()
    private val index = Index()
    private val mutex: Mutex = Mutex()

    suspend fun getAllIndexedItems(indexedItemsFilter: IndexedItemsFilter): List<IndexedItem> {
        mutex.withLock {
            return indexedDocuments.getAllIndexedItems(indexedItemsFilter)
        }
    }

    suspend fun getDocumentThatContainTokenPathsAsync(tokens: List<String>): Deferred<List<AbsolutePath>> = coroutineScope {
        async {
            if (!isActive || tokens.isEmpty()) {
                return@async listOf()
            }

            mutex.withLock {
                val documentIds = tokens
                    .map {
                        async {
                            index.getDocumentThatContainTokenIds(it)
                        }
                    }
                    .awaitAll()

                return@async documentIds
                    .reduce { acc, it -> acc.intersect(it) }
                    .mapNotNull { indexedDocuments.getFileById(it)?.path }
                    .sortedBy { it.asPath() }
                    .toList()
            }
        }
    }

    suspend fun updateWithAsync(
        paths: List<AbsolutePath>,
        indexedItemsFilter: IndexedItemsFilter,
        updateResults: suspend (List<IndexedItem>) -> Unit
    ): Deferred<List<IndexedItem>> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || paths.isEmpty()) {
                        return@async getAllIndexedItems(indexedItemsFilter)
                    }

                    logPathsToIndex(paths)

                    val filesAndDirs = FileManager.splitOnFilesAndDirs(paths)

                    logSplitPaths(filesAndDirs)

                    filesAndDirs.dirs.forEach { indexedDocuments.add(it) }

                    val actor = indexerActor(
                        indexedItemsFilter,
                        (getAllIndexedItems(indexedItemsFilter)).toMutableList(),
                        updateResults
                    )

                    filesAndDirs
                        .getAllFilesUnique()
                        .map { consJobToIndexFileAndRun(actor, it) }
                        .joinAll()

                    val indexedItemsD = CompletableDeferred<List<IndexedItem>>()
                    actor.send(IndexerMessage.GetAllIndexedDocuments(indexedItemsD))
                    val indexedItems = indexedItemsD.await()
                    actor.close()

                    return@async indexedItems
                }
            }
        }

    private fun logPathsToIndex(paths: List<AbsolutePath>) {
        log.debug(String.format("Going to update indexer with %s paths: %s", paths.size, paths))
    }

    private fun logSplitPaths(filesAndDirs: FilesAndDirs) {
        log.debug(
            String.format("Split on files and dirs:\nFiles: %s\nDirs: %s",
                filesAndDirs.files.map { "\t" + it.getPath() },
                filesAndDirs.dirs.flatMap {
                    listOf("\t" + it.path)
                        .plus(it.files.map { file -> "\t\t" + file.getPath() })
                }
            )
        )
    }

    private fun CoroutineScope.consJobToIndexFileAndRun(
        indexerActor: SendChannel<IndexerMessage>,
        file: FilesAndDirs.File
    ) = launch {
        indexerActor.send(IndexerMessage.RemoveDocumentIfPresent(file))

        val document: Document.Tokenized = documentTokenizer.tokenize(DocumentReader.read(file))

        indexerActor.send(IndexerMessage.AddDocument(document, file.isNestedWithDir))

        log.debug("File added: " + file.getPath())
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun CoroutineScope.indexerActor(
        indexedItemsFilter: IndexedItemsFilter,
        indexedItems: MutableList<IndexedItem>,
        updateResults: suspend (List<IndexedItem>) -> Unit
    ) = actor<IndexerMessage> {
        for (msg in channel) {
            when (msg) {
                is IndexerMessage.RemoveDocumentIfPresent -> {
                    val existing: IndexedItem.File? = indexedDocuments.getFileByPath(msg.file.getPath())

                    if (existing != null) {
                        index.remove(existing.id)
                    }
                }

                is IndexerMessage.AddDocument -> {
                    val indexedFile = indexedDocuments.add(msg.document, msg.fileIsNestedWithDir)
                    index.updateWith(msg.document, indexedFile.id)

                    if (indexedItemsFilter.isAny() || !msg.fileIsNestedWithDir) {
                        indexedItems.add(indexedFile)
                        indexedItems.sortWith(Comparator.comparing { it.getPathAsString() })
                        updateResults(indexedItems)
                    }
                }

                is IndexerMessage.GetAllIndexedDocuments -> {
                    msg.response.complete(indexedDocuments.getAllIndexedItems(indexedItemsFilter))
                }
            }
        }
    }

    private sealed class IndexerMessage {
        class RemoveDocumentIfPresent(val file: FilesAndDirs.File) : IndexerMessage()
        class AddDocument(
            val document: Document.Tokenized,
            val fileIsNestedWithDir: Boolean
        ) : IndexerMessage()
        class GetAllIndexedDocuments(val response: CompletableDeferred<List<IndexedItem>>) : IndexerMessage()
    }

    suspend fun removeAsync(
        filesToRemove: List<AbsolutePath>,
        dirsToRemove: List<AbsolutePath>,
        indexedItemsFilter: IndexedItemsFilter
    ): Deferred<List<IndexedItem>> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || (filesToRemove.isEmpty() && dirsToRemove.isEmpty())) {
                        return@async getAllIndexedItems(indexedItemsFilter)
                    }

                    val toRemove = FileManager.defineItemsToRemove(
                        filesToRemove,
                        dirsToRemove,
                        getAllIndexedItems(IndexedItemsFilter.ANY)
                    )

                    val removedDocumentIds = indexedDocuments.remove(toRemove)
                    index.remove(removedDocumentIds)

                    return@async getAllIndexedItems(indexedItemsFilter)
                }
            }
        }

/*    suspend fun syncIndexedItemsAsync(notNestedWithDirOnly: Boolean): Deferred<List<IndexedItem>> = coroutineScope {
        mutex.withLock {
            async {
                if (!isActive) {
                    return@async getAllIndexedItems()
                }
            }
        }
    }*/
}