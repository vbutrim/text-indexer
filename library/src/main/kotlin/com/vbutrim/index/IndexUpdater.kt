package com.vbutrim.index

import java.nio.file.Path

class IndexUpdater(
    private val index: Index,
    private val indexedDocuments: IndexedDocuments,
    private val documentTokenizer: DocumentTokenizer)
{
    fun updateWith(path: Path) {
        val existing: IndexedDocuments.File? = indexedDocuments.getFileByPath(path)

        if (existing != null) {
            index.remove(existing.id)
        }

        val document: Document.Tokenized = documentTokenizer.tokenize(DocumentReader.read(path))
        val id = indexedDocuments.addDocument(document).id
        index.updateWith(document, id)
    }
}