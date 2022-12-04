package com.vbutrim.file

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

        fun fileExists(path: AbsolutePath): Boolean {
            val file = File(path.getPathString())

            return file.exists() && file.isFile
        }

        fun dirContainsAnyFile(path: AbsolutePath): Boolean {
            val dir = File(path.getPathString())

            return dirExists(dir) && getFilesInDir(dir).any()
        }

        fun dirExists(dir: File): Boolean {
            return dir.exists() && dir.isDirectory
        }
    }
}