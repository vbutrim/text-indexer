package com.vbutrim.file

import java.nio.file.Path
import java.time.Instant

class FilesAndDirs(val files: List<File>, val dirs: List<Dir>) {

    fun isEmpty(): Boolean {
        return files.isEmpty() && dirs.isEmpty()
    }

    fun getAllFilesUnique(): Collection<File> {
        return files
            .plus(dirs.flatMap { it.files })
            .distinctBy { it.getPath().getPathAsString() }
    }

    class File private constructor(
        private val file: java.io.File,
        val isIndexedAsNested: Boolean)
    {
        companion object {
            fun cons(file: java.io.File, isIndexedAsNested: Boolean): File {
                return File(file, isIndexedAsNested)
            }
            fun independentSource(file: java.io.File): File {
                return cons(file, false)
            }

            fun indexedAsNested(file: java.io.File): File {
                return cons(file, true)
            }
        }

        fun getPath(): AbsolutePath {
            return file.asAbsolutePath()
        }

        fun readModificationTime(): Instant {
            return file.readModificationTime()
        }

        fun readText(): String {
            return file.readText()
        }
    }

    class Dir(val path: AbsolutePath, internal val files: List<File>) {

        fun getParent(): Path {
            return path.getParentAsPath()
        }

        fun getName(): String {
            return path.getFileName().toString()
        }
    }
}