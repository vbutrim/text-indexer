package com.vbutrim.index

import java.nio.file.Path
import java.time.Instant
import java.util.function.Supplier

object IndexedDocuments {
    private val root: Node = Node.notIndexedDir();
    private val nodeById: MutableMap<Int, Node> = HashMap();
    private var nextId: Int = 0;

    fun addDocument(document: Document.Tokenized): File {
        val node = computeDirNode(document.getPath())
            .computeIfAbsent(document.getFileName()) { Node.file(File.of(nextId++, document)) }

        nodeById.computeIfAbsent(node.asFileOrThrow().id) { node }

        return node.asFileOrThrow()
    }

    fun getFileById(id: Int): File? {
        return nodeById[id]?.asFile()
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
        var current = root;

        for (subDir in path.parent) {
            current = current.computeIfAbsent(subDir.toString(), Node::notIndexedDir)
        }

        return current
    }


    class Node(
        private val children: MutableMap<String, Node>,
        private val file: File?,
        private val isIndexed: Boolean)
    {
        init {
            if (file != null) {
                check(isIndexed)
            }
        }

        companion object {
            fun notIndexedDir(): Node {
                return Node(HashMap(), null, true)
            }

            fun indexedDir(): Node {
                return Node(HashMap(), null, true)
            }

            fun file(file: File): Node {
                return Node(HashMap(), file, true)
            }
        }

        private fun isDocument(): Boolean {
            return file != null
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
    }

    data class File(val id: Int, val path: Path, val modificationTime: Instant) {
        companion object {
            fun of(id: Int, document: Document.Tokenized): File {
                return File(id, document.getPath(), document.getModificationTime())
            }
        }
    }
}
