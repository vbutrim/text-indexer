package com.vbutrim.file

import com.vbutrim.index.IndexedItem
import com.vbutrim.index.ToRemove
import java.io.File

abstract class FileManager {
    companion object {
        fun splitOnFilesAndDirs(paths: List<AbsolutePath>): FilesAndDirs {

            val files = ArrayList<FilesAndDirs.File>()
            val dirs = ArrayList<FilesAndDirs.Dir>()

            for (path in paths) {
                val file = File(path.getPathString())

                if (file.isFile) {
                    files.add(FilesAndDirs.File.independent(file))
                    continue
                }

                dirs.add(
                    FilesAndDirs.Dir(
                        path,
                        getFilesInDir(file).toList()
                    )
                )
            }

            return FilesAndDirs(files, dirs)
        }

        private fun getFilesInDir(dir: File): Sequence<FilesAndDirs.File> {
            require(dir.isDirectory) {
                "not a dir"
            }

            return dir.walkTopDown()
                .filter { it.isFile }
                .map { FilesAndDirs.File.nestedWithDir(it) }
        }

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
                .filter { fileIsExists(it) }
                .flatMap { consAllDirs(it.getParentAsAbsolutePath()) }
                .plus(
                    dirsToRemove
                        .asSequence()
                        .filter { dirContainsAnyFile(it) }
                        .flatMap { consAllDirs(it) }
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

        private fun fileIsExists(path: AbsolutePath): Boolean {
            val file = File(path.getPathString())

            return file.exists() && file.isFile
        }

        private fun dirContainsAnyFile(path: AbsolutePath): Boolean {
            val dir = File(path.getPathString())

            return dir.exists() && dir.isDirectory && getFilesInDir(dir).any()
        }

        private fun consAllDirs(path: AbsolutePath): List<AbsolutePath> {
            var subDirPath = path.getRoot()
            val allDirs = mutableListOf<AbsolutePath>()
                .let {
                    it.add(AbsolutePath.cons(subDirPath))
                    it
                }

            for (subDir in path.asPath()) {
                subDirPath = subDirPath.resolve(subDir)
                allDirs.add(AbsolutePath.cons(subDirPath))
            }

            return allDirs
        }
    }
}