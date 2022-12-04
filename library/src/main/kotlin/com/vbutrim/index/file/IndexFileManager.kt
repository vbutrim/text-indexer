package com.vbutrim.index.file

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FileManager
import com.vbutrim.file.FilesAndDirs
import com.vbutrim.file.readModificationTime
import com.vbutrim.index.IndexedItem
import java.io.File
import java.time.Instant

abstract class IndexFileManager {
    companion object {
        fun defineItemsToRemove(
            filesToRemove: List<AbsolutePath>,
            dirsToRemove: List<AbsolutePath>,
            indexedItems: List<IndexedItem>,
        ): ToRemove {
            if (filesToRemove.isEmpty() && dirsToRemove.isEmpty()) {
                return ToRemove.EMPTY
            }

            return ToRemove.cons(
                filesToRemove,
                dirsToRemove,
                defineDirsToMarkAsNotIndexed(filesToRemove, dirsToRemove, indexedItems)
            )
        }

        private fun defineDirsToMarkAsNotIndexed(
            filesToRemove: List<AbsolutePath>,
            dirsToRemove: List<AbsolutePath>,
            indexedItems: List<IndexedItem>
        ): Set<AbsolutePath> {
            if (filesToRemove.isEmpty() && dirsToRemove.isEmpty()) {
                return setOf()
            }

            return filesToRemove
                .asSequence()
                .filter { FileManager.fileExists(File(it.getPathAsString())) }
                .flatMap { it.getParent().getAllDirPaths() }
                .plus(
                    dirsToRemove
                        .asSequence()
                        .filter { FileManager.dirContainsAnyFile(it) }
                        .flatMap { it.getAllDirPaths() }
                )
                .toSet()
                .intersect(getAllDirs(indexedItems).map { it.path }.toSet())
        }

        private fun getAllDirs(indexedItems: List<IndexedItem>): List<IndexedItem.Dir> {
            val dirs: MutableList<IndexedItem.Dir> = mutableListOf()
            consAllDirs(indexedItems, dirs)
            return dirs
        }

        private fun consAllDirs(indexedItems: List<IndexedItem>, dirs: MutableList<IndexedItem.Dir>) {
            for (indexedItem in indexedItems) {
                when (indexedItem) {
                    is IndexedItem.File -> continue
                    is IndexedItem.Dir -> {
                        dirs.add(indexedItem)
                        consAllDirs(indexedItem.nested, dirs)
                    }
                }
            }
        }

        fun defineItemsToSync(indexedItems: List<IndexedItem>): ToSync {
            val toSyncBuilder = ToSync.builder()
            defineItemsToSync(flatMappedDirs(indexedItems), toSyncBuilder)
            return toSyncBuilder.build()
        }

        private fun flatMappedDirs(items: List<IndexedItem>): List<IndexedItem> {
            return items.flatMap {
                when (it) {
                    is IndexedItem.File -> {
                        listOf(it)
                    }
                    is IndexedItem.Dir -> {
                        flatMappedDirs(it.nested)
                    }
                }
            }
        }

        private fun defineItemsToSync(flatMapped: List<IndexedItem>, toSyncBuilder: ToSync.Builder) {
            for (indexedItem in flatMapped) {
                when (indexedItem) {
                    is IndexedItem.File -> {
                        val file = File(indexedItem.getPathAsString())
                        if (!FileManager.fileExists(file)) {
                            toSyncBuilder.addFileToRemove(indexedItem.path)
                        } else if (indexedItem.isOutDated(file.readModificationTime())) {
                            toSyncBuilder.addFileToAdd(
                                FilesAndDirs.File.cons(
                                    File(indexedItem.getPathAsString()),
                                    indexedItem.isNestedWithDir()
                                )
                            )
                        }
                    }
                    is IndexedItem.Dir -> {
                        val dir = File(indexedItem.getPathAsString())
                        if (!FileManager.dirExists(dir)) {
                            toSyncBuilder.addDirToRemove(indexedItem.path)
                        } else {
                            val dirDiff = DirDiff.cons(
                                indexedItem.nested
                                    .map {
                                        require(it is IndexedItem.File) {
                                            "not a file"
                                        }
                                        it
                                    },
                                FileManager
                                    .getFilesInDir(dir)
                                    .associateBy({ it.getPath() }, { it.readModificationTime() })
                            )

                            dirDiff.filesToAdd().forEach { toSyncBuilder.addFileToAdd(it) }
                            dirDiff.filesToRemove().forEach { toSyncBuilder.addFileToRemove(it) }
                        }
                    }
                }
            }
        }

        private class DirDiff private constructor(
            private val oldFilesInDir: Map<AbsolutePath, IndexedItem.File>,
            private val newFilesInDir: Map<AbsolutePath, Instant>)
        {
            companion object {
                fun cons(oldFilesInDir: List<IndexedItem.File>, newFilesInDir: Map<AbsolutePath, Instant>): DirDiff {
                    return DirDiff(
                        oldFilesInDir.associateBy { it.path },
                        newFilesInDir
                    )
                }
            }

            fun filesToAdd(): List<FilesAndDirs.File> {
                return getNewFiles()
                    .plus(getOutDatedFiles())
                    .toList()
            }

            private fun getNewFiles(): Sequence<FilesAndDirs.File> {
                return newFilesInDir.keys.minus(oldFilesInDir.keys)
                    .asSequence()
                    .map { FilesAndDirs.File.nestedWithDir(File(it.getPathAsString())) }
            }

            private fun getOutDatedFiles(): Sequence<FilesAndDirs.File> {
                return oldFilesInDir
                    .entries
                    .asSequence()
                    .map { file ->
                        newFilesInDir[file.key]?.let {
                            Pair(file, it)
                        }
                    }
                    .filterNotNull()
                    .filter { it.first.value.isOutDated(it.second) }
                    .map { outDatedFile ->
                        FilesAndDirs.File.cons(
                            File(outDatedFile.first.key.getPathAsString()),
                            outDatedFile.first.value.isNestedWithDir()
                        )
                    }
            }

            fun filesToRemove(): Set<AbsolutePath> {
                return oldFilesInDir.keys.minus(newFilesInDir.keys)
            }
        }
    }
}