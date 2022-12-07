package com.vbutrim.index.file

import com.vbutrim.file.AbsolutePath
import java.nio.file.Path

class ToRemove private constructor(
    private val filesToRemove: Set<Path>,
    private val dirsToRemove: Set<Path>,
    private val dirsToMarkAsNotIndexed: Set<Path>
) {
    companion object {
        val EMPTY = cons(listOf(), listOf(), listOf())

        fun cons(
            filesToRemove: Collection<AbsolutePath>,
            dirsToRemove: Collection<AbsolutePath>,
            dirsToMarkAsNotIndexed: Collection<AbsolutePath>
        ): ToRemove {
            return ToRemove(
                toUniquePaths(filesToRemove),
                toUniquePaths(dirsToRemove),
                toUniquePaths(dirsToMarkAsNotIndexed)
            )
        }

        private fun toUniquePaths(paths: Collection<AbsolutePath>): Set<Path> {
            return paths.map { it.asPath() }.toSet()
        }
    }

    fun isEmpty(): Boolean {
        return filesToRemove.isEmpty() && dirsToRemove.isEmpty() && dirsToMarkAsNotIndexed.isEmpty()
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

    fun dirShouldBeMarkedAsNotIndexedByAbsolutePath(path: Path): Boolean {
        require(path.isAbsolute) {
            "not an absolute path"
        }

        return dirsToMarkAsNotIndexed.contains(path)
    }
}