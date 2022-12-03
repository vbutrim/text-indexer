package com.vbutrim.index

import java.util.*
import kotlin.collections.HashMap

object Index {
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
        documentIdsByToken.values
            .forEach { it.removeAll(documentIds.asList().toSet()) }

        documentIdsByToken.entries
            .removeIf { it.value.isEmpty() }
    }
}
