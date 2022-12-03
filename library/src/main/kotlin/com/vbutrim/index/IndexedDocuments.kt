package com.vbutrim.index

import com.vbutrim.file.FilesAndDirs
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.function.Supplier
import kotlin.io.path.listDirectoryEntries

object IndexedDocuments {
    private val root: Node = Node.notIndexedDir();
    private val fileNodeById: MutableMap<Int, Node> = HashMap();
    private var nextId: Int = 0;

    fun add(dir: FilesAndDirs.Dir) {
        val dirNode = computeDirNode(dir.getParent())

        dirNode.computeIfAbsent(dir.getName()) { Node.indexedDir() }
    }

    fun add(document: Document.Tokenized): File {
        val fileNode = computeDirNode(document.getDir())
            .computeIfAbsent(document.getFileName()) { Node.file(File.of(nextId++, document)) }

        fileNodeById.computeIfAbsent(fileNode.asFileOrThrow().id) { fileNode }

        return fileNode.asFileOrThrow()
    }

    fun getFileById(id: Int): File? {
        return fileNodeById[id]?.asFile()
    }

    fun getFileByPath(path: Path): File? {
        return getDirNodeOrNull(path)
            ?.getOrNull(path.fileName.toString())
            ?.asFile()
    }

    private fun getDirNodeOrNull(path: Path): Node? {
        var current: Node? = root;

        for (subDir in path.parent) {
            current = current?.getOrNull(subDir.toString())

            if (current == null) {
                break;
            }
        }

        return current
    }

    private fun computeDirNode(path: Path): Node {
        var current = root.computeIfAbsent(path.root.toString(), Node::notIndexedDir)

        for (subDir in path) {
            current = current.computeIfAbsent(subDir.toString(), Node::notIndexedDir)
        }

        return current
    }

    fun getAllIndexedPaths(): List<Item> {
        return dfs(root, Paths.get(""))
    }

    private fun dfs(parent: Node, parentPath: Path): List<Item> {
        if (parent.isDocument()) {
            return listOf()
        }

        val items = arrayListOf<Item>()

        for (child in parent.getSortedChildren()) {
            items.addAll(dfs(child.value, parentPath.resolve(child.key)))
        }

        if (parent.isDir() && parent.isIndexed) {
            return listOf(Dir(parentPath, items))
        }

        return items
    }

    class Node(
        private val children: MutableMap<String, Node>,
        private val file: File?,
        internal val isIndexed: Boolean
    ) {
        init {
            if (file != null) {
                check(isIndexed)
            }
        }

        companion object {
            fun notIndexedDir(): Node {
                return Node(HashMap(), null, false)
            }

            fun indexedDir(): Node {
                return Node(HashMap(), null, true)
            }

            fun file(file: File): Node {
                return Node(HashMap(), file, true)
            }
        }

        fun isDocument(): Boolean {
            return file != null
        }

        fun isDir(): Boolean {
            return !isDocument()
        }

        fun asFile(): File? {
            return file;
        }

        fun asFileOrThrow(): File {
            return asFile()!!
        }

        fun getOrNull(child: String): Node? {
            return children[child]
        }

        fun contains(child: String): Boolean {
            return children.containsKey(child)
        }

        fun computeIfAbsent(child: String, creationSupplier: Supplier<Node>): Node {
            require(!isDocument()) {
                "impossible to add child for final document"
            }
            return children.computeIfAbsent(child) { creationSupplier.get() }
        }

        fun getSortedChildren(): Collection<MutableMap.MutableEntry<String, Node>> {
            return children.entries.sortedBy { it.key }
        }
    }

    sealed class Item(private val path: Path) {
        fun getPathAsString(): String {
            return path.toString()
        }
    }

    data class File(val path: Path, val id: Int, val modificationTime: Instant) : Item(path) {
        companion object {
            fun of(id: Int, document: Document.Tokenized): File {
                return File(document.getPath(), id, document.getModificationTime())
            }
        }
    }

    class Dir(path: Path, val nested: List<Item>) : Item(path) {
    }
}
