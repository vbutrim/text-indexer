package com.vbutrim.index

import java.util.*

internal object Index {
    private val documentIdsByToken: MutableMap<String, MutableSet<Int>> = HashMap()

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

    fun remove(vararg documentIds: Int) {
        remove(documentIds.asList())
    }

    fun remove(documentIds: Collection<Int>) {
        if (documentIds.isEmpty()) {
            return
        }

        documentIdsByToken.values
            .forEach { it.removeAll(documentIds.toSet()) }

        documentIdsByToken.entries
            .removeIf { it.value.isEmpty() }
    }
}
