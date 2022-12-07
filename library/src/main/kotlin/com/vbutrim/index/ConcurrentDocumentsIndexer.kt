package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlin.coroutines.CoroutineContext

class ConcurrentDocumentsIndexer(private val actor: SendChannel<Message>) {

    companion object {
        suspend fun createActor(context: CoroutineContext, documentsIndexer: DocumentsIndexer): SendChannel<Message> = coroutineScope {
            return@coroutineScope documentsIndexerActor(context, documentsIndexer)
        }

        @OptIn(ObsoleteCoroutinesApi::class)
        fun CoroutineScope.documentsIndexerActor(
            context: CoroutineContext,
            documentsIndexer: DocumentsIndexer
        ) = actor<Message>(context) {
            for (msg in channel) {
                when (msg) {
                    is Message.GetDocumentThatContainTokenPath -> msg.result.complete(
                        documentsIndexer.getDocumentThatContainTokenPathsAsync(msg.tokens).await()
                    )

                    is Message.GetIndexedItems -> msg.result.complete(
                        documentsIndexer.getIndexedItems(msg.indexedItemsFilter)
                    )

                    is Message.Remove -> msg.result.complete(
                        documentsIndexer
                            .removeAsync(msg.filesToRemove, msg.dirsToRemove, msg.indexedItemsFilter)
                            .await()
                    )

                    is Message.SyncIndexedItems -> msg.result.complete(
                        documentsIndexer
                            .syncIndexedItemsAsync(msg.indexedItemsFilter, msg.onStartToSync)
                            .await()
                    )

                    is Message.UpdateWith -> msg.result.complete(
                        documentsIndexer
                            .updateWithAsync(msg.toIndex, msg.indexedItemsFilter, msg.updateResults)
                            .await()
                    )
                }
            }
        }
    }

    suspend fun getIndexedItems(indexedItemsFilter: IndexedItemsFilter): List<IndexedItem> {
        val result = CompletableDeferred<List<IndexedItem>>()
        actor.send(Message.GetIndexedItems(indexedItemsFilter, result))
        return result.await()
    }

    suspend fun getDocumentThatContainTokenPathsAsync(tokens: List<String>): Deferred<List<AbsolutePath>> {
        val result = CompletableDeferred<List<AbsolutePath>>()
        actor.send(Message.GetDocumentThatContainTokenPath(tokens, result))
        return result
    }

    suspend fun updateWithAsync(
        toIndex: List<AbsolutePath>,
        indexedItemsFilter: IndexedItemsFilter,
        updateResults: suspend (List<IndexedItem>) -> Unit
    ): Deferred<DocumentsIndexer.Result> {
        val result = CompletableDeferred<DocumentsIndexer.Result>()
        actor.send(Message.UpdateWith(toIndex, indexedItemsFilter, updateResults, result))
        return result
    }

    suspend fun removeAsync(
        filesToRemove: List<AbsolutePath>,
        dirsToRemove: List<AbsolutePath>,
        indexedItemsFilter: IndexedItemsFilter
    ): Deferred<DocumentsIndexer.Result> {
        val result = CompletableDeferred<DocumentsIndexer.Result>()
        actor.send(Message.Remove(filesToRemove, dirsToRemove, indexedItemsFilter, result))
        return result
    }

    suspend fun syncIndexedItemsAsync(
        indexedItemsFilter: IndexedItemsFilter,
        onStartToSync: suspend () -> Unit
    ): Deferred<DocumentsIndexer.Result> {
        val result = CompletableDeferred<DocumentsIndexer.Result>()
        actor.send(Message.SyncIndexedItems(indexedItemsFilter, onStartToSync, result))
        return result
    }

    sealed class Message {
        class GetIndexedItems(
            val indexedItemsFilter: IndexedItemsFilter,
            val result: CompletableDeferred<List<IndexedItem>>
        ) : Message()

        class GetDocumentThatContainTokenPath(
            val tokens: List<String>,
            val result: CompletableDeferred<List<AbsolutePath>>
        ) : Message()

        class UpdateWith(
            val toIndex: List<AbsolutePath>,
            val indexedItemsFilter: IndexedItemsFilter,
            val updateResults: suspend (List<IndexedItem>) -> Unit,
            val result: CompletableDeferred<DocumentsIndexer.Result>
        ) : Message()

        class Remove(
            val filesToRemove: List<AbsolutePath>,
            val dirsToRemove: List<AbsolutePath>,
            val indexedItemsFilter: IndexedItemsFilter,
            val result: CompletableDeferred<DocumentsIndexer.Result>
        ) : Message()

        class SyncIndexedItems(
            val indexedItemsFilter: IndexedItemsFilter,
            val onStartToSync: suspend () -> Unit,
            val result: CompletableDeferred<DocumentsIndexer.Result>
        ) : Message()
    }
}