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
                        getFilesInDir(file)
                    )
                )
            }

            return FilesAndDirs(files, dirs)
        }

        private fun getFilesInDir(dir: File): List<FilesAndDirs.File> {
            require(dir.isDirectory) {
                "not a dir"
            }

            return dir.walkTopDown()
                .filter { it.isFile }
                .map { FilesAndDirs.File.nestedWithDir(it) }
                .toList()
        }

        fun defineItemsToRemove(
            pathsToRemove: List<AbsolutePath>,
            indexedItems: List<IndexedItem>
        ): ToRemove {
            return ToRemove.EMPTY
        }
    }
}