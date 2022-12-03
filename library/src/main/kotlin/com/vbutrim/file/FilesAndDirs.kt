package com.vbutrim.file

import java.nio.file.Path
import java.time.Instant

class FilesAndDirs(private val files: List<File>, val dirs: List<Dir>) {

    fun getAllFilesUnique(): Collection<File> {
        return files
            .plus(dirs.flatMap { it.files })
            .distinctBy { it.getPath().getPathString() }
    }

    class File private constructor(
        private val file: java.io.File,
        val isNestedWithDir: Boolean)
    {
        companion object {
            fun independent(file: java.io.File): File {
                return File(file, false)
            }

            fun nestedWithDir(file: java.io.File): File {
                return File(file, true)
            }
        }

        fun getPath(): AbsolutePath {
            return AbsolutePath.cons(file.toPath())
        }

        fun readModificationTime(): Instant {
            return Instant.ofEpochMilli(file.lastModified())
        }

        fun readText(): String {
            return file.readText()
        }
    }

    class Dir(private val path: AbsolutePath, internal val files: List<File>) {

        fun getParent(): Path {
            return path.getParent()
        }

        fun getName(): String {
            return path.getFileName().toString()
        }
    }
}