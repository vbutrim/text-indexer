package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FilesAndDirs
import java.nio.file.Path

class IndexedDocuments {
    private val root: Node.Dir = Node.Dir.notIndexed()
    private val fileNodeById: MutableMap<Int, Node.File> = HashMap()
    private var nextId: Int = 0

    fun add(dir: FilesAndDirs.Dir) {
        computeDirNode(dir.getParent())
            .computeIfAbsent(dir.getName()) { Node.Dir.indexedIndependently() }
            .let {
                require(it is Node.Dir) {
                    "not a dirNode"
                }

                it.setIndexed()
                it.setNotNestedWithDir()
                DepthFirstSearch.markAllSubdirectoriesAsIndexed(it)
            }
    }

    fun add(
        document: Document.Tokenized,
        isNestedWithDir: Boolean
    ): IndexedItem.File {
        val fileNode = computeDirNode(document.getDir())
            .computeIfAbsent(document.getFileName()) {
                Node.File.cons(
                    IndexedItem.File.of(nextId++, document, isNestedWithDir)
                )
            }

        require(fileNode is Node.File) {
            "not a fileNode"
        }

        if (!isNestedWithDir) {
            fileNode.setNotNestedWithDir()
        }

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

        for (subDir in path.getParent()) {
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
                .computeIfAbsent(subDir.toString()) { Node.Dir.cons(shouldBeMarkedAsIndexed) }
                .let {
                    markAsIndexedIf(shouldBeMarkedAsIndexed, it)
                    it
                }
        }

        require(current is Node.Dir) {
            "not a dirNode"
        }
        return current
    }

    private fun markAsIndexedIf(shouldMarkAsIndexed: Boolean, current: Node) {
        if (shouldMarkAsIndexed) {
            when (current) {
                is Node.File -> {}
                is Node.Dir -> {
                    current.setIndexed()
                }
            }
        }
    }

    fun getAllIndexedItems(notNestedWithDirOnly: Boolean): List<IndexedItem> {
        return DepthFirstSearch.getAllIndexedPaths(root, notNestedWithDirOnly)
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
