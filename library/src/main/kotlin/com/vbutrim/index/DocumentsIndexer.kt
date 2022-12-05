package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FilesAndDirs
import com.vbutrim.index.file.IndexedFileManager
import com.vbutrim.index.file.ToRemove
import com.vbutrim.index.file.ToSync
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

    suspend fun getIndexedItems(indexedItemsFilter: IndexedItemsFilter): List<IndexedItem> {
        mutex.withLock {
            return indexedDocuments.getIndexedItems(indexedItemsFilter)
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
        toIndex: FilesAndDirs,
        indexedItemsFilter: IndexedItemsFilter,
        updateResults: suspend (List<IndexedItem>) -> Unit
    ): Deferred<Updated> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || toIndex.isEmpty()) {
                        return@async Updated.Nothing
                    }

                    log(toIndex)

                    toIndex.dirs.forEach { indexedDocuments.add(it) }

                    val actor = indexerActor(
                        indexedItemsFilter,
                        (getIndexedItems(indexedItemsFilter)).toMutableList(),
                        updateResults
                    )

                    toIndex
                        .getAllFilesUnique()
                        .map { consJobToIndexFileAndRun(actor, it) }
                        .joinAll()

                    val indexedItemsD = CompletableDeferred<List<IndexedItem>>()
                    actor.send(IndexerMessage.GetAllIndexedDocuments(indexedItemsD))
                    val indexedItems = indexedItemsD.await()
                    actor.close()

                    return@async Updated.Some(indexedItems)
                }
            }
        }

    sealed class Updated {
        object Nothing: Updated()

        class Some(val finalIndexedItems: List<IndexedItem>) : Updated()
    }

    private fun log(filesAndDirs: FilesAndDirs) {
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
                    msg.response.complete(indexedDocuments.getIndexedItems(indexedItemsFilter))
                }

                is IndexerMessage.Remove -> {
                    remove(msg.toRemove)
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

        class Remove(val toRemove: ToRemove) : IndexerMessage()
    }

    suspend fun getDocumentsToRemove(
        filesToRemove: List<AbsolutePath>,
        dirsToRemove: List<AbsolutePath>
    ): ToRemove {
        if (filesToRemove.isEmpty() && dirsToRemove.isEmpty()) {
            return ToRemove.EMPTY
        }

        return IndexedFileManager.defineItemsToRemove(
            filesToRemove,
            dirsToRemove,
            getIndexedItems(IndexedItemsFilter.ANY)
        )
    }

    suspend fun removeAsync(
        toRemove: ToRemove,
        indexedItemsFilter: IndexedItemsFilter
    ): Deferred<Removed> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || toRemove.isEmpty()) {
                        return@async Removed.Nothing
                    }

                    remove(toRemove)

                    return@async Removed.Some(getIndexedItems(indexedItemsFilter))
                }
            }
        }

    sealed class Removed {
        object Nothing : Removed()

        class Some(val finalIndexedItems: List<IndexedItem>) : Removed()
    }

    private fun remove(toRemove: ToRemove) {
        val removedDocumentIds = indexedDocuments.remove(toRemove)
        index.remove(removedDocumentIds)
    }

    suspend fun getIndexedDocumentsToSync(): ToSync {
        val indexedItems = getIndexedItems(IndexedItemsFilter.ANY)

        if (indexedItems.isEmpty()) {
            return ToSync.EMPTY
        }

        return IndexedFileManager.defineItemsToSync(indexedItems)
    }

    suspend fun syncIndexedItemsAsync(toSync: ToSync, indexedItemsFilter: IndexedItemsFilter): Deferred<Synced> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || toSync.isEmpty()) {
                        return@async Synced.Nothing
                    }

                    val actor = indexerActor(
                        indexedItemsFilter,
                        (getIndexedItems(indexedItemsFilter)).toMutableList()
                    ) {}

                    toSync
                        .filesToAdd
                        .map { consJobToIndexFileAndRun(actor, it) }
                        .joinAll()

                    actor.send(IndexerMessage.Remove(toSync.toRemove))

                    val indexedItemsD = CompletableDeferred<List<IndexedItem>>()
                    actor.send(IndexerMessage.GetAllIndexedDocuments(indexedItemsD))
                    val indexedItems = indexedItemsD.await()
                    actor.close()

                    return@async Synced.Some(indexedItems)
                }
            }
    }

    sealed class Synced {
        object Nothing : Synced()

        class Some(val finalIndexedItems: List<IndexedItem>) : Synced()
    }
}