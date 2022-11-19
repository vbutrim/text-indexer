package com.vbutrim.index

import java.util.*

class Index(private val documentIdsByToken: MutableMap<String, MutableSet<Int>>) {

    fun getDocumentThatContainTokenIds(token: String): Set<Int> {
        return documentIdsByToken[token] ?: Collections.emptySet()
    }

    fun updateWith(document: Document.Tokenized, documentId: Int) {
        remove(documentId)
        document.tokens
            .forEach {
                documentIdsByToken
                    .computeIfAbsent(it) { HashSet() }
                    .add(documentId)
            }
    }

    fun remove(documentId: Int) {
        documentIdsByToken.values
            .forEach { it.remove(documentId) }

        documentIdsByToken.entries
            .removeIf { it.value.isEmpty() }
    }
}
