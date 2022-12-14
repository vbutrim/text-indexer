package com.vbutrim.file

import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * This is wrapper not to operate #toAbsolutePath() in every place
 */
class AbsolutePath private constructor(private val absolutePath: Path) {
    companion object {
        fun cons(path: Path): AbsolutePath {
            return AbsolutePath(path.toAbsolutePath())
        }
    }

    fun getPathAsString(): String {
        return absolutePath.pathString
    }

    fun getRoot(): Path {
        return absolutePath.root
    }

    fun getParent(): AbsolutePath {
        return AbsolutePath(getParentAsPath())
    }

    fun getParentAsPath(): Path {
        return absolutePath.parent
    }

    fun getFileName(): Path {
        return absolutePath.fileName
    }

    fun asPath(): Path {
        return absolutePath
    }

    fun getAllDirPaths(): List<AbsolutePath> {
        var subDirPath = getRoot()
        val allDirs = mutableListOf<AbsolutePath>()
            .let {
                it.add(subDirPath.asAbsolutePath())
                it
            }

        for (subDir in asPath()) {
            subDirPath = subDirPath.resolve(subDir)
            allDirs.add(subDirPath.asAbsolutePath())
        }

        return allDirs
    }

    override fun toString(): String {
        return absolutePath.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbsolutePath

        if (absolutePath != other.absolutePath) return false

        return true
    }

    override fun hashCode(): Int {
        return absolutePath.hashCode()
    }
}