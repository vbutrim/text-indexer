package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FilesAndDirs
import java.nio.file.Path
import java.time.Instant

object IndexedDocuments {
    internal val root: Node.Dir = Node.Dir.notIndexed()
    private val fileNodeById: MutableMap<Int, Node.File> = HashMap()
    private var nextId: Int = 0

    fun add(dir: FilesAndDirs.Dir) {
        computeDirNode(dir.getParent())
            .computeIfAbsent(dir.getName()) { Node.Dir.indexed() }
            .let {
                require(it is Node.Dir) {
                    "not a dirNode"
                }

                it.setIndexed()
            }
    }

    fun add(document: Document.Tokenized, isNestedWithDir: Boolean): File {
        val fileNode = computeDirNode(document.getDir()).computeIfAbsent(document.getFileName()) {
            Node.File.cons(
                File.of(nextId++, document, isNestedWithDir)
            )
        }

        require(fileNode is Node.File) {
            "not a fileNode"
        }

        fileNodeById.computeIfAbsent(fileNode.getId()) { fileNode }

        return fileNode.asFile()
    }

    fun getFileById(id: Int): File? {
        return fileNodeById[id]?.asFile()
    }

    fun getFileByPath(path: AbsolutePath): File? {
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

        for (subDir in path) {
            require(current is Node.Dir) {
                "not a dirNode"
            }
            current = current.computeIfAbsent(subDir.toString(), Node.Dir::notIndexed)
        }

        require(current is Node.Dir) {
            "not a dirNode"
        }
        return current
    }

    fun getAllIndexedPaths(): List<Item> {
        return DepthFirstSearch.getAllIndexedPaths()
    }

    /**
     * @return all removed document ids
     */
    fun remove(toRemove: ToRemove): Set<Int> {
        if (toRemove.isEmpty()) {
            return setOf()
        }

        return DepthFirstSearch.removeAll(toRemove)
    }

    sealed class Item(private val path: AbsolutePath) {
        fun getPathAsString(): String {
            return path.toString()
        }
    }

    data class File constructor(
        val path: AbsolutePath,
        val id: Int,
        val modificationTime: Instant,
        val isNestedWithDir: Boolean
    ) : Item(path) {
        companion object {
            fun of(id: Int, document: Document.Tokenized, isNestedWithDir: Boolean): File {
                return File(document.getPath(), id, document.getModificationTime(), isNestedWithDir)
            }
        }
    }

    class Dir(path: AbsolutePath, val nested: List<Item>) : Item(path)
}
