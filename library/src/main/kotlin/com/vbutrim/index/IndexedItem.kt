package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import java.time.Instant

sealed class IndexedItem(open val path: AbsolutePath) {
    fun getPathAsString(): String {
        return path.toString()
    }

    data class Dir(override val path: AbsolutePath, val nested: List<IndexedItem>) : IndexedItem(path)

    data class File constructor(
        override val path: AbsolutePath,
        val id: Int,
        private var modificationTime: Instant,
        private var isNestedWithDir: Boolean
    ) : IndexedItem(path) {
        companion object {
            fun of(id: Int, document: Document.Tokenized, isNestedWithDir: Boolean): File {
                return File(document.getPath(), id, document.getModificationTime(), isNestedWithDir)
            }
        }

        fun isNestedWithDir(): Boolean {
            return isNestedWithDir
        }

        fun setNotNestedWithDir() {
            isNestedWithDir = false
        }

        fun setModificationTime(modificationTime: Instant) {
            this.modificationTime = modificationTime
        }

        fun isOutDated(lastModificationTime: Instant): Boolean {
            return modificationTime.isBefore(lastModificationTime)
        }
    }
}