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
        val isNestedWithDir: Boolean)
    {
        companion object {
            fun cons(file: java.io.File, isNestedWithDir: Boolean): File {
                return File(file, isNestedWithDir)
            }
            fun independent(file: java.io.File): File {
                return cons(file, false)
            }

            fun nestedWithDir(file: java.io.File): File {
                return cons(file, true)
            }
        }

        fun getPath(): AbsolutePath {
            return AbsolutePath.cons(file.toPath())
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