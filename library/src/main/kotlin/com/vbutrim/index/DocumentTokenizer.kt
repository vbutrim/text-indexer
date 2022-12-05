package com.vbutrim.index

import java.util.*
import java.util.stream.Stream

interface DocumentTokenizer {
    fun collectTokens(content: String): Stream<String>

    fun tokenize(document: Document.WithContent): Document.Tokenized {
        return document.tokenized(
            collectTokens(document.content).toList()
        )
    }

    /**
     * split text based on any symbol or space
     */
    class BasedOnWordSeparation: DocumentTokenizer {
        override fun collectTokens(content: String): Stream<String> {
            return Collections
                .list(StringTokenizer(content.lowercase().trim(), " -/.,;:()`<>'!?\"\t\n\r\u000c"))
                .stream()
                .map { token -> token as String }
        }
    }
}