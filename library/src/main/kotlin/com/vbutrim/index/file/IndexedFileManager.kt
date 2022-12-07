package com.vbutrim.index.file

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FileManager
import com.vbutrim.file.FilesAndDirs
import com.vbutrim.file.readModificationTime
import com.vbutrim.index.IndexedItem
import com.vbutrim.index.flatMappedFilesInDirs
import java.io.File
import java.time.Instant

abstract class IndexedFileManager {
    companion object {
        /**
         * If there is a file, which is going to be removed and still exists in the directory, there is need to mark
         * this directory as not to be indexed fully, only left files that are present already in the indexed documents.
         * The same is for directories: if existing directory is removed, then parent directories should be marked as not
         * to be indexed.
         */
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

        /**
         * Defines items to sync based on actual state of the file system:
         * - new added files / directories;
         * - removed files / directories;
         * - files that has been changed (based on last modification time).
         */
        fun defineItemsToSync(indexedItems: List<IndexedItem>): ToSync {
            val toSyncBuilder = ToSync.builder()
            defineItemsToSync(indexedItems, toSyncBuilder)
            return toSyncBuilder.build()
        }

        private fun defineItemsToSync(indexedItems: List<IndexedItem>, toSyncBuilder: ToSync.Builder) {
            for (indexedItem in indexedItems.flatMappedFilesInDirs()) {
                when (indexedItem) {
                    is IndexedItem.File -> {
                        addFileToSyncIfApplicable(indexedItem, toSyncBuilder)
                    }
                    is IndexedItem.Dir -> {
                        defineItemsToSync(indexedItem, toSyncBuilder)
                    }
                }
            }
        }

        private fun addFileToSyncIfApplicable(
            indexedFile: IndexedItem.File,
            toSyncBuilder: ToSync.Builder
        ) {
            val file = File(indexedFile.getPathAsString())
            if (!FileManager.fileExists(file)) {
                toSyncBuilder.addFileToRemove(indexedFile.path)
            } else if (indexedFile.isOutDated(file.readModificationTime())) {
                toSyncBuilder.addFileToAdd(
                    FilesAndDirs.File.cons(
                        File(indexedFile.getPathAsString()),
                        indexedFile.isIndexedAsNested()
                    )
                )
            }
        }

        private fun defineItemsToSync(
            indexedDir: IndexedItem.Dir,
            toSyncBuilder: ToSync.Builder
        ) {
            val dir = File(indexedDir.getPathAsString())
            if (!FileManager.dirExists(dir)) {
                toSyncBuilder.addDirToRemove(indexedDir.path)
            } else {
                val dirDiff = DirDiff.cons(
                    indexedDir.nested
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
                    .map { FilesAndDirs.File.indexedAsNested(File(it.getPathAsString())) }
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
                            outDatedFile.first.value.isIndexedAsNested()
                        )
                    }
            }

            fun filesToRemove(): Set<AbsolutePath> {
                return oldFilesInDir.keys.minus(newFilesInDir.keys)
            }
        }
    }
}