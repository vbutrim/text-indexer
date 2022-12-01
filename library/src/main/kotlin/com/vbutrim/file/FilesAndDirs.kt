package com.vbutrim.file

import java.nio.file.Path
import java.time.Instant

class FilesAndDirs(private val files: List<File>, val dirs: List<Dir>) {

    fun getAllFilesUnique(): Collection<File> {
        return files
            .plus(dirs.flatMap { it.files })
            .distinctBy { it.getPath().toAbsolutePath().toString() }
    }

    class File(private val file: java.io.File) {
        fun getPath(): Path {
            return file.toPath()
        }

        fun readModificationTime(): Instant {
            return Instant.ofEpochMilli(file.lastModified())
        }

        fun readText(): String {
            return file.readText()
        }
    }

    class Dir(val path: Path, internal val files: List<File>) {

        fun getParent(): Path {
            return path.parent
        }

        fun getName(): String {
            return path.fileName.toString()
        }
    }
}