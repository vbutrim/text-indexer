package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import java.time.Instant

sealed class IndexedItem(private val path: AbsolutePath) {
    fun getPathAsString(): String {
        return path.toString()
    }

    class Dir(path: AbsolutePath, val nested: List<IndexedItem>) : IndexedItem(path)

    data class File constructor(
        val path: AbsolutePath,
        val id: Int,
        val modificationTime: Instant,
        val isNestedWithDir: Boolean
    ) : IndexedItem(path) {
        companion object {
            fun of(id: Int, document: Document.Tokenized, isNestedWithDir: Boolean): File {
                return File(document.getPath(), id, document.getModificationTime(), isNestedWithDir)
            }
        }
    }
}