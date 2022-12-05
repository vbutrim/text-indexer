package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FileManager
import com.vbutrim.file.FilesAndDirs
import com.vbutrim.index.file.IndexedFileManager
import com.vbutrim.index.file.ToRemove
import com.vbutrim.index.file.ToSync
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.sync.Mutex
import org.slf4j.Logger
import java.util.*

private val log: Logger = org.slf4j.LoggerFactory.getLogger(DocumentsIndexer::class.java)

class DocumentsIndexer(
    private val documentTokenizer: DocumentTokenizer
) {
    private val indexedDocuments = IndexedDocuments()
    private val index = Index()
    private val mutex: Mutex = Mutex()

    suspend fun getIndexedItems(indexedItemsFilter: IndexedItemsFilter): List<IndexedItem> {
        val indexedItems: List<IndexedItem>
        try {
            mutex.lock()
            indexedItems = indexedDocuments.getIndexedItems(indexedItemsFilter)
        } finally {
            mutex.unlock()
        }
        return indexedItems
    }

    suspend fun getDocumentThatContainTokenPathsAsync(tokens: List<String>): Deferred<List<AbsolutePath>> =
        coroutineScope {
            async {
                if (!isActive || tokens.isEmpty()) {
                    return@async listOf()
                }

                val documentPaths: List<AbsolutePath>
                try {
                    mutex.lock()
                    log.debug("updateWithAsync() method executing")

                    documentPaths = tokens
                        .map {
                            async {
                                index.getDocumentThatContainTokenIds(it)
                            }
                        }
                        .awaitAll()
                        .reduce { acc, it -> acc.intersect(it) }
                        .mapNotNull { indexedDocuments.getFileById(it)?.path }
                        .sortedBy { it.asPath() }
                        .toList()
                } finally {
                    mutex.unlock()
                }

                return@async documentPaths

            }
        }

    suspend fun updateWithAsync(
        toIndex: List<AbsolutePath>,
        indexedItemsFilter: IndexedItemsFilter,
        updateResults: suspend (List<IndexedItem>) -> Unit
    ): Deferred<Updated> =
        coroutineScope {
            async {
                if (!isActive || toIndex.isEmpty()) {
                    return@async Updated.Nothing
                }

                val updated: Updated
                try {
                    mutex.lock()
                    log.debug("updateWithAsync() method executing")
                    updated = updateWith(toIndex, indexedItemsFilter, updateResults)
                } finally {
                    mutex.unlock()
                }
                return@async updated
            }
        }

    private suspend fun updateWith(
        toIndex: List<AbsolutePath>,
        indexedItemsFilter: IndexedItemsFilter,
        updateResults: suspend (List<IndexedItem>) -> Unit
    ): Updated = coroutineScope {
        val filesAndDirs = FileManager.splitOnFilesAndDirs(toIndex)

        if (filesAndDirs.isEmpty()) {
            return@coroutineScope Updated.Nothing
        }

        log(filesAndDirs)

        filesAndDirs.dirs.forEach { indexedDocuments.add(it) }

        val actor = indexerActor(
            indexedItemsFilter,
            (indexedDocuments.getIndexedItems(IndexedItemsFilter.ANY)).toMutableList(),
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
        return@coroutineScope Updated.Some(indexedItems)
    }

    sealed class Updated {
        object Nothing : Updated()

        class Some(val finalIndexedItems: List<IndexedItem>) : Updated()
    }

    private fun log(filesAndDirs: FilesAndDirs) {
        log.debug(
            String.format("Split on files and dirs:\nFiles: %s\nDirs: %s",
                filesAndDirs.files.map { "\n" + it.getPath() },
                filesAndDirs.dirs.flatMap {
                    listOf("\n" + it.path)
                        .plus(it.files.map { file -> "\n\t" + file.getPath() })
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

    suspend fun removeAsync(
        filesToRemove: List<AbsolutePath>,
        dirsToRemove: List<AbsolutePath>,
        indexedItemsFilter: IndexedItemsFilter
    ): Deferred<Removed> =
        coroutineScope {
            async {
                if (!isActive) {
                    return@async Removed.Nothing
                }

                val removed: Removed
                try {
                    mutex.lock()
                    log.debug("removeAsync() method executing")

                    removed = remove(filesToRemove, dirsToRemove, indexedItemsFilter)
                } finally {
                    mutex.unlock()
                }

                return@async removed
            }
        }

    private fun remove(
        filesToRemove: List<AbsolutePath>,
        dirsToRemove: List<AbsolutePath>,
        indexedItemsFilter: IndexedItemsFilter
    ): Removed {
        val toRemove = getDocumentsToRemove(filesToRemove, dirsToRemove)

        if (toRemove.isEmpty()) {
            return Removed.Nothing
        }

        remove(toRemove)

        return Removed.Some(indexedDocuments.getIndexedItems(indexedItemsFilter))
    }

    private fun getDocumentsToRemove(
        filesToRemove: List<AbsolutePath>,
        dirsToRemove: List<AbsolutePath>
    ): ToRemove {
        if (filesToRemove.isEmpty() && dirsToRemove.isEmpty()) {
            return ToRemove.EMPTY
        }

        return IndexedFileManager.defineItemsToRemove(
            filesToRemove,
            dirsToRemove,
            indexedDocuments.getIndexedItems(IndexedItemsFilter.ANY)
        )
    }

    private fun remove(toRemove: ToRemove) {
        val removedDocumentIds = indexedDocuments.remove(toRemove)
        index.remove(removedDocumentIds)
    }

    suspend fun syncIndexedItemsAsync(indexedItemsFilter: IndexedItemsFilter): Deferred<Synced> =
        coroutineScope {
            async {
                if (!isActive) {
                    return@async Synced.Nothing
                }

                val synced: Synced
                try {
                    mutex.lock()
                    log.debug("syncIndexedItemsAsync() method executing")
                    synced = syncIndexedItems(indexedItemsFilter)
                } finally {
                    mutex.unlock()
                }
                return@async synced
            }
        }

    private suspend fun syncIndexedItems(
        indexedItemsFilter: IndexedItemsFilter
    ): Synced = coroutineScope {
        val toSync = getIndexedDocumentsToSync()

        if (toSync.isEmpty()) {
            log.debug("Nothing to sync")
            return@coroutineScope Synced.Nothing
        }

        val actor = indexerActor(
            indexedItemsFilter,
            (indexedDocuments.getIndexedItems(IndexedItemsFilter.ANY)).toMutableList()
        ) {}

        toSync
            .filesToAdd
            .map { consJobToIndexFileAndRun(actor, it) }
            .joinAll()

        actor.send(IndexerMessage.Remove(toSync.toRemove))

        val indexedItemsD = CompletableDeferred<List<IndexedItem>>()
        actor.send(IndexerMessage.GetAllIndexedDocuments(indexedItemsD))
        val synced = Synced.Some(indexedItemsD.await())
        actor.close()

        return@coroutineScope synced
    }

    private fun getIndexedDocumentsToSync(): ToSync {
        val indexedItems = indexedDocuments.getIndexedItems(IndexedItemsFilter.ANY)

        if (indexedItems.isEmpty()) {
            return ToSync.EMPTY
        }

        return IndexedFileManager.defineItemsToSync(indexedItems)
    }

    sealed class Removed {
        object Nothing : Removed()

        class Some(val finalIndexedItems: List<IndexedItem>) : Removed()
    }

    sealed class Synced {
        object Nothing : Synced()

        class Some(val finalIndexedItems: List<IndexedItem>) : Synced()
    }
}