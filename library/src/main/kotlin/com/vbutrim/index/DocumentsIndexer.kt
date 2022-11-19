package com.vbutrim.index

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

class DocumentsIndexer(
    private val index: Index,
    private val indexedDocuments: IndexedDocuments,
    private val documentTokenizer: DocumentTokenizer)
{
    private val mutex: Mutex = Mutex();

    suspend fun getDocumentThatContainTokenPaths(vararg tokens: String): List<Path> {
        mutex.withLock {
            return tokens
                .map { index.getDocumentThatContainTokenIds(it) }
                .reduce { acc, it -> acc.intersect(it) }
                .mapNotNull { indexedDocuments.getFileById(it) }
                .map { it.path }
                .sorted()
        }
    }

    suspend fun updateWith(path: Path) {
        val existing: IndexedDocuments.File? = indexedDocuments.getFileByPath(path)

        if (existing != null) {
            index.remove(existing.id)
        }

        val document: Document.Tokenized = documentTokenizer.tokenize(DocumentReader.read(path))

        mutex.withLock {
            val id = indexedDocuments.addDocument(document).id
            index.updateWith(document, id)
        }
    }
}