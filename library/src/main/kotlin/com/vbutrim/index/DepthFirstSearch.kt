package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import java.nio.file.Path

internal abstract class DepthFirstSearch {
    companion object {
        fun getAllIndexedPaths(): List<IndexedDocuments.Item> {
            return IndexedDocuments.root
                .getSortedChildren()
                .flatMap { dfsOnGetAllIndexedPaths(it.value, Path.of(it.key)) }
        }

        private fun dfsOnGetAllIndexedPaths(current: Node, path: Path): List<IndexedDocuments.Item> {
            return when (current) {
                is Node.File -> {
                    return listOf(current.asFile())
                }

                is Node.Dir -> {
                    dfsOnGetAllIndexedPaths(current, path)
                }
            }
        }

        private fun dfsOnGetAllIndexedPaths(current: Node.Dir, path: Path): List<IndexedDocuments.Item> {
            val items = arrayListOf<IndexedDocuments.Item>()

            for (child in current.getSortedChildren()) {
                items.addAll(dfsOnGetAllIndexedPaths(child.value, path.resolve(child.key)))
            }

            if (current.isIndexed()) {
                return listOf(IndexedDocuments.Dir(AbsolutePath.cons(path), items))
            }

            return items
        }

        fun removeAll(toRemove: ToRemove): Set<Int> {
            if (toRemove.isEmpty()) {
                return setOf()
            }

            val removedDocumentIds: MutableSet<Int> = hashSetOf()

            IndexedDocuments.root
                .getSortedChildren()
                .forEach { dfsOnRemovePaths(it.value, Path.of(it.key), toRemove, false, removedDocumentIds) }

            return removedDocumentIds
        }

        private fun dfsOnRemovePaths(
            current: Node,
            path: Path,
            toRemove: ToRemove,
            removeForcibly: Boolean,
            removedDocumentIds: MutableSet<Int>
        ): Boolean {
            when (current) {
                is Node.File -> {
                    return if (removeForcibly || toRemove.containsFileByAbsolutePath(path)) {
                        removedDocumentIds.add(current.getId())
                        true
                    } else {
                        false
                    }
                }

                is Node.Dir -> {
                    return dfsOnRemovePaths(
                        current,
                        path,
                        toRemove,
                        removeForcibly,
                        removedDocumentIds
                    )
                }
            }
        }

        private fun dfsOnRemovePaths(
            current: Node.Dir,
            path: Path,
            toRemove: ToRemove,
            removeForcibly: Boolean,
            removedDocumentIds: MutableSet<Int>
        ): Boolean {
            val childrenToRemove: MutableSet<String> = hashSetOf()

            for (child in current.getSortedChildren()) {
                dfsOnRemovePaths(
                    child.value,
                    path.resolve(child.key),
                    toRemove,
                    removeForcibly || toRemove.containsDirByAbsolutePath(path),
                    removedDocumentIds
                )
                    .let {
                        if (it) {
                            childrenToRemove.add(child.key)
                        }
                    }
            }

            current.removeAll(childrenToRemove)

            if (toRemove.isDirAsMarkedNotIndexedByAbsolutePath(path)) {
                current.setNotIndexed()
            }

            return removeForcibly
                    || toRemove.containsDirByAbsolutePath(path)
                    || !current.isIndexed() && !current.hasAnyChild()
        }
    }
}