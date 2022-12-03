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

    private abstract class DepthFirstSearch {
        companion object {
            fun getAllIndexedPaths(): List<Item> {
                return root
                    .getSortedChildren()
                    .flatMap { dfsOnGetAllIndexedPaths(it.value, Path.of(it.key)) }
            }

            private fun dfsOnGetAllIndexedPaths(current: Node, path: Path): List<Item> {
                return when (current) {
                    is Node.File -> {
                        return listOf(current.asFile())
                    }

                    is Node.Dir -> {
                        dfsOnGetAllIndexedPaths(current, path)
                    }
                }
            }

            private fun dfsOnGetAllIndexedPaths(current: Node.Dir, path: Path): List<Item> {
                val items = arrayListOf<Item>()

                for (child in current.getSortedChildren()) {
                    items.addAll(dfsOnGetAllIndexedPaths(child.value, path.resolve(child.key)))
                }

                if (current.isIndexed()) {
                    return listOf(Dir(AbsolutePath.cons(path), items))
                }

                return items
            }

            fun removeAll(toRemove: ToRemove): Set<Int> {
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
        }
    }
}
