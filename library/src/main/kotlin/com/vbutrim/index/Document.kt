package com.vbutrim.index

import java.nio.file.Path
import java.time.Instant

data class Document(val path: Path, val modificationTime: Instant) {
    fun getFileName(): String {
        return path.fileName.toString()
    }

    data class WithContent(val document: Document, val content: String) {
        fun tokenized(tokens: List<String>): Tokenized {
            return Tokenized(document, tokens)
        }
    }

    data class Tokenized(val document: Document, val tokens: List<String>) {
        fun getPath(): Path {
            return document.path
        }

        fun getDir(): Path {
            return getPath().parent;
        }

        fun getFileName(): String {
            return document.getFileName()
        }

        fun getModificationTime(): Instant {
            return document.modificationTime
        }
    }
}

