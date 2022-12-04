package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import java.nio.file.Path

internal abstract class DepthFirstSearch {
    companion object {
        /**
         * @implNote start dfs with root's children to construct path correctly.
         */
        fun getAllIndexedPaths(root: Node.Dir, userSelectionOnly: Boolean): List<IndexedItem> {
            return root
                .getSortedChildren()
                .flatMap { dfsOnGetAllIndexedPaths(it.value, Path.of(it.key), userSelectionOnly) }
        }

        private fun dfsOnGetAllIndexedPaths(
            current: Node,
            path: Path,
            userSelectionOnly: Boolean
        ): List<IndexedItem> {
            return when (current) {
                is Node.File -> {
                    return if (!userSelectionOnly || !current.isNestedWithDir()) {
                        listOf(current.asFile())
                    } else {
                        listOf()
                    }
                }

                is Node.Dir -> {
                    dfsOnGetAllIndexedPaths(current, path, userSelectionOnly)
                }
            }
        }

        private fun dfsOnGetAllIndexedPaths(current: Node.Dir, path: Path, userSelectionOnly: Boolean): List<IndexedItem> {
            val items = arrayListOf<IndexedItem>()

            for (child in current.getSortedChildren()) {
                items.addAll(dfsOnGetAllIndexedPaths(child.value, path.resolve(child.key), userSelectionOnly))
            }

            if (current.isIndexed()) {
                return listOf(
                    IndexedItem.Dir(
                        AbsolutePath.cons(path),
                        if (userSelectionOnly) {
                            items
                        } else {
                            flatMappedDirs(items)
                        }
                    )
                )
            }

            return items
        }

        private fun flatMappedDirs(items: List<IndexedItem>): List<IndexedItem> {
            return items.flatMap {
                when (it) {
                    is IndexedItem.File -> {
                        listOf(it)
                    }
                    is IndexedItem.Dir -> {
                        it.nested
                    }
                }
            }
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

            if (toRemove.isDirAsMarkedNotIndexedByAbsolutePath(path)) {
                current.setNotIndexed()
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