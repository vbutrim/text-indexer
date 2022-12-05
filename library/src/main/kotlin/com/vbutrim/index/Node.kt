package com.vbutrim.index

import java.time.Instant
import java.util.function.Supplier

internal sealed class Node {
    abstract fun isNestedWithDir(): Boolean

    class File(private val file: IndexedItem.File) : Node() {
        companion object {
            fun cons(file: IndexedItem.File): File {
                return File(file)
            }
        }

        fun getId(): Int {
            return file.id
        }

        override fun isNestedWithDir(): Boolean{
            return file.isNestedWithDir()
        }

        fun asFile(): IndexedItem.File {
            return file
        }

        fun setModificationTime(modificationTime: Instant) {
            file.setModificationTime(modificationTime)
        }

        fun setNotNestedWithDir() {
            file.setNotNestedWithDir()
        }
    }

    class Dir(
        private var isIndexed: Boolean,
        private var isNestedWithDir: Boolean?
    ) : Node() {

        private val children: MutableMap<String, Node> = HashMap()

        companion object {
            fun cons(isIndexed: Boolean, isNestedWithDir: Boolean? = null): Dir {
                return Dir(isIndexed, isNestedWithDir)
            }

            fun notIndexed(): Dir {
                return cons(isIndexed = false)
            }

            fun indexedIndependently(): Dir {
                return cons(isIndexed = true, isNestedWithDir = false)
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

        fun setIndexed() {
            if (isIndexed) {
                return
            }
            isIndexed = true
        }

        fun setNotIndexedAndNestedWithDirAgnostic() {
            isIndexed = false
            isNestedWithDir = null
        }

        fun isIndexed(): Boolean {
            return isIndexed
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

        override fun isNestedWithDir(): Boolean {
            return isNestedWithDir ?: true
        }

        fun setNotNestedWithDir() {
            isNestedWithDir = false
        }
    }
}