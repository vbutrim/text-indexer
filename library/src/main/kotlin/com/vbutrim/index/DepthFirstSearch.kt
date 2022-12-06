package com.vbutrim.index

import com.vbutrim.file.asAbsolutePath
import com.vbutrim.index.file.ToRemove
import java.nio.file.Path

/**
 * This is a helper to walk through the trie.
 * @see IndexedDocuments
 */
internal abstract class DepthFirstSearch {
    companion object {
        /**
         * @implNote it's needed to start dfs with root's children to construct path correctly.
         */
        fun getAllIndexedPaths(root: Node.Dir, indexedItemsFilter: IndexedItemsFilter): List<IndexedItem> {
            return root
                .getSortedChildren()
                .flatMap { dfsOnGetAllIndexedPaths(it.value, Path.of(it.key), indexedItemsFilter) }
        }

        private fun dfsOnGetAllIndexedPaths(
            current: Node,
            path: Path,
            indexedItemsFilter: IndexedItemsFilter
        ): List<IndexedItem> {
            return when (current) {
                is Node.File -> {
                    return if (indexedItemsFilter.isAny() || !current.isNestedWithDir()) {
                        listOf(current.asFile())
                    } else {
                        listOf()
                    }
                }

                is Node.Dir -> {
                    dfsOnGetAllIndexedPaths(current, path, indexedItemsFilter)
                }
            }
        }

        private fun dfsOnGetAllIndexedPaths(current: Node.Dir, path: Path, indexedItemsFilter: IndexedItemsFilter): List<IndexedItem> {
            val items = arrayListOf<IndexedItem>()

            for (child in current.getSortedChildren()) {
                items.addAll(dfsOnGetAllIndexedPaths(child.value, path.resolve(child.key), indexedItemsFilter))
            }

            if (current.isIndexed() && (indexedItemsFilter.isAny() || !current.isNestedWithDir())) {
                return listOf(IndexedItem.Dir(path.asAbsolutePath(), items))
            }

            return items
        }

        fun removeAll(root: Node.Dir, toRemove: ToRemove): Set<Int> {
            if (toRemove.isEmpty()) {
                return setOf()
            }

            val removedDocumentIds: MutableSet<Int> = hashSetOf()

            root
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

            if (toRemove.dirShouldBeMarkedAsNotIndexedByAbsolutePath(path)) {
                current.setNotIndexedAndNestedWithDirAgnostic()
            }

            return removeForcibly
                    || toRemove.containsDirByAbsolutePath(path)
                    || !current.isIndexed() && !current.hasAnyChild()
        }

        fun markAllSubdirectoriesAsIndexed(current: Node.Dir) {
            for (child in current.getChildren()) {
                markAllSubdirectoriesAsIndexed(child.value)
            }
        }

        private fun markAllSubdirectoriesAsIndexed(current: Node) {
            when (current) {
                is Node.File -> {
                    return
                }

                is Node.Dir -> {
                    current.setIndexed()
                    markAllSubdirectoriesAsIndexed(current)
                }
            }
        }
    }
}