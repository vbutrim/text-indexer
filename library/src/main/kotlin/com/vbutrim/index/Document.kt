package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import java.nio.file.Path
import java.time.Instant

data class Document(val path: AbsolutePath, val modificationTime: Instant) {
    fun getFileName(): String {
        return path.getFileName().toString()
    }

    data class WithContent(val document: Document, val content: String) {
        fun tokenized(tokens: List<String>): Tokenized {
            return Tokenized(document, tokens)
        }
    }

    data class Tokenized(val document: Document, val tokens: List<String>) {
        fun getPath(): AbsolutePath {
            return document.path
        }

        fun getDir(): Path {
            return getPath().getParent()
        }

        fun getFileName(): String {
            return document.getFileName()
        }

        fun getModificationTime(): Instant {
            return document.modificationTime
        }
    }
}

