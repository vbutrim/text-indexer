package com.vbutrim.index

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

object DocumentsIndexer {
    private val documentTokenizer: DocumentTokenizer = DocumentTokenizer.BasedOnWordSeparation()
    private val mutex: Mutex = Mutex();

    suspend fun getDocumentThatContainTokenPaths(tokens: List<String>): List<Path> {
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
        mutex.withLock {
            val existing: IndexedDocuments.File? = IndexedDocuments.getFileByPath(path)

            if (existing != null) {
                Index.remove(existing.id)
            }

            val document: Document.Tokenized = documentTokenizer.tokenize(DocumentReader.read(path))

            val id = IndexedDocuments.addDocument(document).id
            Index.updateWith(document, id)
        }
    }
}