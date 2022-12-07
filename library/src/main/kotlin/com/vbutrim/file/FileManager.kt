package com.vbutrim.file

import java.io.File

abstract class FileManager {
    companion object {
        fun splitOnFilesAndDirs(paths: List<AbsolutePath>): FilesAndDirs {

            val files = ArrayList<FilesAndDirs.File>()
            val dirs = ArrayList<FilesAndDirs.Dir>()

            for (path in paths) {
                val file = File(path.getPathAsString())

                if (file.isFile) {
                    files.add(FilesAndDirs.File.independentSource(file))
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

        fun getFilesInDir(dir: File): Sequence<FilesAndDirs.File> {
            require(dir.isDirectory) {
                "not a dir"
            }

            return dir.walkTopDown()
                .filter { it.isFile }
                .map { FilesAndDirs.File.indexedAsNested(it) }
        }

        fun fileExists(file: File): Boolean {
            return file.exists() && file.isFile
        }

        fun dirContainsAnyFile(path: AbsolutePath): Boolean {
            val dir = File(path.getPathAsString())

            return dirExists(dir) && getFilesInDir(dir).any()
        }

        fun dirExists(dir: File): Boolean {
            return dir.exists() && dir.isDirectory
        }
    }
}