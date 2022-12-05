package com.vbutrim.index.file

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.FilesAndDirs

class ToSync private constructor(val filesToAdd: List<FilesAndDirs.File>, val toRemove: ToRemove) {

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }

    fun isEmpty(): Boolean {
        return filesToAdd.isEmpty() && toRemove.isEmpty()
    }

    class Builder {
        private val filesToAdd = mutableListOf<FilesAndDirs.File>()
        private val filesToRemove = mutableListOf<AbsolutePath>()
        private val dirsToRemove = mutableListOf<AbsolutePath>()

        fun addFileToAdd(toAdd: FilesAndDirs.File) {
            filesToAdd.add(toAdd)
        }

        fun addFileToRemove(path: AbsolutePath) {
            filesToRemove.add(path)
        }

        fun addDirToRemove(path: AbsolutePath) {
            dirsToRemove.add(path)
        }

        fun build(): ToSync {
            return ToSync(
                filesToAdd,
                ToRemove.cons(
                    filesToRemove,
                    dirsToRemove,
                    listOf()
                )
            )
        }
    }
}