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
}