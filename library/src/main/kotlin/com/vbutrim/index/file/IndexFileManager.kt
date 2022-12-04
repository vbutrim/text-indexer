package com.vbutrim.index.file

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FileManager
import com.vbutrim.index.IndexedItem
import com.vbutrim.index.ToRemove

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
                .filter { FileManager.fileExists(it) }
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
    }
}