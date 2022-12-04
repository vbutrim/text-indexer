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

object DocumentsIndexer {
    private val documentTokenizer: DocumentTokenizer = DocumentTokenizer.BasedOnWordSeparation()
    private val mutex: Mutex = Mutex()

    /**
     * @param notNestedWithDirOnly defines if there is need to return all indexed paths or paths, which user actually selected
     * @return indexed paths considering notNestedWithDirOnly flag
     */
    suspend fun getAllIndexedItems(notNestedWithDirOnly: Boolean): List<IndexedItem> {
        mutex.withLock {
            return IndexedDocuments.getAllIndexedItems(notNestedWithDirOnly)
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
                            Index.getDocumentThatContainTokenIds(it)
                        }
                    }
                    .awaitAll()

                return@async documentIds
                    .reduce { acc, it -> acc.intersect(it) }
                    .mapNotNull { IndexedDocuments.getFileById(it)?.path }
                    .sortedBy { it.asPath() }
                    .toList()
            }
        }
    }

    suspend fun updateWithAsync(
        paths: List<AbsolutePath>,
        notNestedWithDirOnly: Boolean,
        updateResults: suspend (List<IndexedItem>) -> Unit
    ): Deferred<List<IndexedItem>> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || paths.isEmpty()) {
                        return@async getAllIndexedItems(notNestedWithDirOnly)
                    }

                    logPathsToIndex(paths)

                    val filesAndDirs = FileManager.splitOnFilesAndDirs(paths)

                    logSplitPaths(filesAndDirs)

                    filesAndDirs.dirs.forEach { IndexedDocuments.add(it) }

                    val actor = indexerActor(
                        notNestedWithDirOnly,
                        (getAllIndexedItems(notNestedWithDirOnly)).toMutableList(),
                        updateResults
                    )

                    filesAndDirs
                        .getAllFilesUnique()
                        .map { consJobToIndexFileAndRun(actor, it) }
                        .joinAll()

                    actor.close()

                    return@async getAllIndexedItems(notNestedWithDirOnly)
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
        notNestedWithDirOnly: Boolean,
        indexedItems: MutableList<IndexedItem>,
        updateResults: suspend (List<IndexedItem>) -> Unit
    ) = actor<IndexerMessage> {
        for (msg in channel) {
            when (msg) {
                is IndexerMessage.RemoveDocumentIfPresent -> {
                    val existing: IndexedItem.File? = IndexedDocuments.getFileByPath(msg.file.getPath())

                    if (existing != null) {
                        Index.remove(existing.id)
                    }
                }

                is IndexerMessage.AddDocument -> {
                    val indexedFile = IndexedDocuments.add(msg.document, msg.fileIsNestedWithDir)
                    Index.updateWith(msg.document, indexedFile.id)

                    if (!notNestedWithDirOnly || !msg.fileIsNestedWithDir) {
                        indexedItems.add(indexedFile)
                        indexedItems.sortWith(Comparator.comparing { it.getPathAsString() })
                        updateResults(indexedItems)
                    }
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
    }

    suspend fun removeAsync(
        filesToRemove: List<AbsolutePath>,
        dirsToRemove: List<AbsolutePath>,
        notNestedWithDirOnly: Boolean
    ): Deferred<List<IndexedItem>> =
        coroutineScope {
            mutex.withLock {
                async {
                    if (!isActive || (filesToRemove.isEmpty() && dirsToRemove.isEmpty())) {
                        return@async getAllIndexedItems(notNestedWithDirOnly)
                    }

                    val toRemove = FileManager.defineItemsToRemove(
                        filesToRemove,
                        dirsToRemove,
                        getAllIndexedItems(false)
                    )

                    val removedDocumentIds = IndexedDocuments.remove(toRemove)
                    Index.remove(removedDocumentIds)

                    return@async getAllIndexedItems(notNestedWithDirOnly)
                }
            }
        }
}