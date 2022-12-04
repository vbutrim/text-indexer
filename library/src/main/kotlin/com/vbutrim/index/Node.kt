package com.vbutrim.index

import java.util.function.Supplier

internal sealed class Node {
    class File(private val file: IndexedItem.File) : Node() {
        companion object {
            fun cons(file: IndexedItem.File): File {
                return File(file)
            }
        }

        fun getId(): Int {
            return file.id
        }

        fun isNestedWithDir(): Boolean{
            return file.isNestedWithDir
        }

        fun asFile(): IndexedItem.File {
            return file
        }
    }

    class Dir(private var isIndexed: Boolean) : Node() {

        private val children: MutableMap<String, Node> = HashMap()

        companion object {
            fun cons(isIndexed: Boolean): Dir {
                return Dir(isIndexed)
            }
            fun notIndexed(): Dir {
                return cons(false)
            }

            fun indexed(): Dir {
                return cons(true)
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

        fun setNotIndexed() {
            isIndexed = false
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
    }
}