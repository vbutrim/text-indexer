package com.vbutrim.file

import java.nio.file.Path
import kotlin.io.path.pathString

class AbsolutePath private constructor(private val absolutePath: Path) {
    companion object {
        fun cons(path: Path): AbsolutePath {
            return AbsolutePath(path.toAbsolutePath())
        }
    }

    fun getPathString(): String {
        return absolutePath.pathString
    }

    fun getRoot(): Path {
        return absolutePath.root
    }

    fun getParentAsAbsolutePath(): AbsolutePath {
        return AbsolutePath(getParent())
    }

    fun getParent(): Path {
        return absolutePath.parent
    }

    fun getFileName(): Path {
        return absolutePath.fileName
    }

    fun asPath(): Path {
        return absolutePath
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