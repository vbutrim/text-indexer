package com.vbutrim.index

import java.time.Instant
import java.util.function.Supplier

internal sealed class Node {
    abstract fun isIndexedAsNested(): Boolean

    class File(private val file: IndexedItem.File) : Node() {
        companion object {
            fun cons(file: IndexedItem.File): File {
                return File(file)
            }
        }

        fun getId(): Int {
            return file.id
        }

        override fun isIndexedAsNested(): Boolean{
            return file.isIndexedAsNested()
        }

        fun asFile(): IndexedItem.File {
            return file
        }

        fun setModificationTime(modificationTime: Instant) {
            file.setModificationTime(modificationTime)
        }

        fun setIsIndexedAsIndependentSource() {
            file.setIsIndexedAsIndependentSource()
        }
    }

    class Dir(
        private var indexedStatus: IndexedStatus
    ) : Node() {

        private val children: MutableMap<String, Node> = HashMap()

        companion object {
            private fun cons(indexedStatus: IndexedStatus): Dir {
                return Dir(indexedStatus)
            }

            fun cons(isIndexedAsNested: Boolean): Dir {
                return cons(
                    if (isIndexedAsNested) {
                        IndexedStatus.Indexed.AS_NESTED
                    } else {
                        IndexedStatus.NotIndexed
                    }
                )
            }

            fun notIndexed(): Dir {
                return cons(IndexedStatus.NotIndexed)
            }

            fun indexedIndependently(): Dir {
                return cons(IndexedStatus.Indexed.INDEPENDENTLY)
            }
        }

        fun getOrNull(child: String): Node? {
            return children[child]
        }

        fun computeIfAbsent(child: String, creationSupplier: Supplier<Node>): Node {
            return children.computeIfAbsent(child) { creationSupplier.get() }
        }

        fun getChildren(): Set<MutableMap.MutableEntry<String, Node>> {
            return children.entries
        }

        fun getSortedChildren(): Collection<MutableMap.MutableEntry<String, Node>> {
            return getChildren().sortedBy { it.key }
        }

        /**
         * considering current status: not to override INDEPENDENTLY
         */
        fun setIndexedAsNested() {
            if (isIndexed()) {
                return
            }
            indexedStatus = IndexedStatus.Indexed.AS_NESTED
        }

        fun setNotIndexed() {
            indexedStatus = IndexedStatus.NotIndexed
        }

        fun isIndexed(): Boolean {
            return when(indexedStatus) {
                is IndexedStatus.Indexed -> true
                is IndexedStatus.NotIndexed -> false
            }
        }

        fun removeAll(childrenToRemove: Set<String>) {
            if (childrenToRemove.isEmpty()) {
                return
            }
            childrenToRemove.forEach { children.remove(it) }
        }

        fun hasAnyChild(): Boolean {
            return children.isNotEmpty()
        }

        override fun isIndexedAsNested(): Boolean {
            return when(indexedStatus) {
                is IndexedStatus.NotIndexed -> false
                is IndexedStatus.Indexed -> (indexedStatus as IndexedStatus.Indexed).isIndexedAsNested
            }
        }

        fun setIsIndexedAsIndependentSource() {
            indexedStatus = IndexedStatus.Indexed.INDEPENDENTLY
        }

        internal sealed class IndexedStatus {
            object NotIndexed : IndexedStatus()

            class Indexed private constructor(val isIndexedAsNested: Boolean): IndexedStatus() {
                companion object {
                    val INDEPENDENTLY = Indexed(false)
                    val AS_NESTED = Indexed(true)
                }
            }
        }
    }
}