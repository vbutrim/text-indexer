package com.vbutrim.index

import com.vbutrim.file.AbsolutePath
import java.nio.file.Path

internal class ToRemove private constructor(
    private val filesToRemove: Set<Path>,
    private val dirsToRemove: Set<Path>,
    private val dirsToMarkAsNotIndexed: Set<Path>
) {
    companion object {
        fun cons(
            filesToRemove: List<AbsolutePath>,
            dirsToRemove: List<AbsolutePath>,
            dirsToMarkAsNotIndexed: List<AbsolutePath>
        ): ToRemove {
            return ToRemove(
                toUniquePaths(filesToRemove),
                toUniquePaths(dirsToRemove),
                toUniquePaths(dirsToMarkAsNotIndexed)
            )
        }

        private fun toUniquePaths(paths: List<AbsolutePath>): Set<Path> {
            return paths.map { it.asPath() }.toSet()
        }
    }

    fun isEmpty(): Boolean {
        return filesToRemove.isEmpty() && dirsToRemove.isEmpty()
    }

    fun containsFileByAbsolutePath(path: Path): Boolean {
        require(path.isAbsolute) {
            "not an absolute path"
        }

        return filesToRemove.contains(path)
    }

    fun containsDirByAbsolutePath(path: Path): Boolean {
        require(path.isAbsolute) {
            "not an absolute path"
        }

        return dirsToRemove.contains(path)
    }

    fun isDirAsMarkedNotIndexedByAbsolutePath(path: Path): Boolean {
        require(path.isAbsolute) {
            "not an absolute path"
        }

        return dirsToMarkAsNotIndexed.contains(path)
    }
}