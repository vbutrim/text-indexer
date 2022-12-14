package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FilesAndDirs
import com.vbutrim.index.file.ToRemove
import java.nio.file.Path

/**
 * Stores items (files and directories), which have been marked to index. Not to store links in inverted index each file
 * has id, which is actually stored in the mapping.
 * To optimize walking through directories and files, trie (prefix tree) algorithm is used to store paths as a graph.
 * @see Index
 */
class IndexedDocuments {
    private val root: Node.Dir = Node.Dir.notIndexed()

    /**
     * Not to walk the trie on each #getFileById() request and quickly make a response
     */
    private val fileNodeById: MutableMap<Int, Node.File> = HashMap()
    private var nextId: Int = 0

    /**
     * Creates directory node if it's not created yet and marks it as an independent source.
     */
    fun add(dir: FilesAndDirs.Dir) {
        computeDirNode(dir.getParent())
            .computeIfAbsent(dir.getName()) { Node.Dir.indexedIndependently() }
            .let {
                require(it is Node.Dir) {
                    "not a dirNode"
                }

                it.setIsIndexedAsIndependentSource()
                DepthFirstSearch.markAllSubdirsAsIndexedAsNested(it)
            }
    }

    /**
     * Creates file node if it's not created yet and marks it as independent source.
     */
    fun add(
        document: Document.Tokenized,
        isIndexedAsNested: Boolean
    ): IndexedItem.File {
        val fileNode = computeDirNode(document.getDir())
            .computeIfAbsent(document.getFileName()) {
                Node.File.cons(
                    IndexedItem.File.cons(nextId++, document, isIndexedAsNested)
                )
            }

        require(fileNode is Node.File) {
            "not a fileNode"
        }

        if (!isIndexedAsNested) {
            fileNode.setIsIndexedAsIndependentSource()
        }

        fileNode.setModificationTime(document.getModificationTime())

        fileNodeById.computeIfAbsent(fileNode.getId()) { fileNode }

        return fileNode.asFile()
    }

    fun getFileById(id: Int): IndexedItem.File? {
        return fileNodeById[id]?.asFile()
    }


    fun getFileByPath(path: AbsolutePath): IndexedItem.File? {
        return getDirNodeOrNull(path)?.getOrNull(path.getFileName().toString())?.let {
            when (it) {
                is Node.File -> {
                    it.asFile()
                }

                is Node.Dir -> {
                    null
                }
            }
        }
    }

    private fun getDirNodeOrNull(path: AbsolutePath): Node.Dir? {
        var current: Node? = root.getOrNull(path.getRoot().toString())

        for (subDir in path.getParentAsPath()) {
            if (current == null) {
                break
            }

            current = getSubDirIfDirNode(current, subDir)
        }

        return current?.let {
            when (it) {
                is Node.File -> {
                    return null
                }

                is Node.Dir -> {
                    return it
                }
            }
        }
    }

    private fun getSubDirIfDirNode(current: Node, subDir: Path): Node? {
        return when (current) {
            is Node.File -> {
                return null
            }

            is Node.Dir -> {
                current.getOrNull(subDir.toString())
            }
        }
    }

    private fun computeDirNode(path: Path): Node.Dir {
        var current = root.computeIfAbsent(path.root.toString(), Node.Dir::notIndexed)

        var shouldBeMarkedAsIndexed = when (current) {
            is Node.File -> false
            is Node.Dir -> current.isIndexed()
        }

        for (subDir in path) {
            require(current is Node.Dir) {
                "not a dirNode"
            }

            shouldBeMarkedAsIndexed = shouldBeMarkedAsIndexed || current.isIndexed()
            current = current
                .computeIfAbsent(subDir.toString()) {
                    Node.Dir.cons(shouldBeMarkedAsIndexed)
                }
                .let {
                    markAsIndexedAsNestedIf(shouldBeMarkedAsIndexed, it)
                    it
                }
        }

        require(current is Node.Dir) {
            "not a dirNode"
        }
        return current
    }

    private fun markAsIndexedAsNestedIf(shouldBeMarkAsIndexed: Boolean, current: Node) {
        if (shouldBeMarkAsIndexed) {
            when (current) {
                is Node.File -> {}
                is Node.Dir -> {
                    current.setIndexedAsNested()
                }
            }
        }
    }

    fun getIndexedItems(indexedItemsFilter: IndexedItemsFilter): List<IndexedItem> {
        return DepthFirstSearch.getAllIndexedPaths(root, indexedItemsFilter)
    }

    /**
     * @return all removed document ids
     */
    fun remove(toRemove: ToRemove): Set<Int> {
        if (toRemove.isEmpty()) {
            return setOf()
        }

        return DepthFirstSearch.removeAll(root, toRemove)
    }
}
