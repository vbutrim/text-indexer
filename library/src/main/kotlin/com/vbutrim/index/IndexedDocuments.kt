package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FilesAndDirs
import java.nio.file.Path
import java.time.Instant
import java.util.function.Supplier

object IndexedDocuments {
    private val root: Node.Dir = Node.Dir.notIndexed()
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

    fun add(document: Document.Tokenized): File {
        val fileNode = computeDirNode(document.getDir()).computeIfAbsent(document.getFileName()) {
            Node.File.cons(
                    File.of(
                        nextId++,
                        document
                    )
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
        return root
            .getSortedChildren()
            .flatMap { depthFirstSearch(it.value, Path.of(it.key)) }
    }

    private fun depthFirstSearch(parent: Node, parentPath: Path): List<Item> {
        return when (parent) {
            is Node.File -> {
                return listOf(parent.asFile())
            }

            is Node.Dir -> {
                depthFirstSearch(parent, parentPath)
            }
        }
    }

    private fun depthFirstSearch(parent: Node.Dir, parentPath: Path): List<Item> {
        val items = arrayListOf<Item>()

        for (child in parent.getSortedChildren()) {
            items.addAll(depthFirstSearch(child.value, parentPath.resolve(child.key)))
        }

        if (parent.isIndexed()) {
            return listOf(Dir(AbsolutePath.cons(parentPath), items))
        }

        return items
    }

    private sealed class Node {
        class File(private val file: IndexedDocuments.File) : Node() {
            companion object {
                fun cons(file: IndexedDocuments.File): File {
                    return File(file)
                }
            }

            fun getId(): Int {
                return file.id
            }

            fun asFile(): IndexedDocuments.File {
                return file
            }
        }

        class Dir(private var isIndexed: Boolean) : Node() {

            private val children: MutableMap<String, Node> = HashMap()

            companion object {
                fun notIndexed(): Dir {
                    return Dir(false)
                }

                fun indexed(): Dir {
                    return Dir(true)
                }
            }

            fun getOrNull(child: String): Node? {
                return children[child]
            }

            fun computeIfAbsent(child: String, creationSupplier: Supplier<Node>): Node {
                return children.computeIfAbsent(child) { creationSupplier.get() }
            }

            fun getSortedChildren(): Collection<MutableMap.MutableEntry<String, Node>> {
                return children.entries.sortedBy { it.key }
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
        }
    }

    sealed class Item(private val path: AbsolutePath) {
        fun getPathAsString(): String {
            return path.toString()
        }
    }

    data class File constructor(val path: AbsolutePath, val id: Int, val modificationTime: Instant) : Item(path) {
        companion object {
            fun of(id: Int, document: Document.Tokenized): File {
                return File(document.getPath(), id, document.getModificationTime())
            }
        }
    }

    class Dir(path: AbsolutePath, val nested: List<Item>) : Item(path)
}
