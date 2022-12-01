package com.vbutrim.index

import com.vbutrim.file.FilesAndDirs

object DocumentReader {
    fun read(file: FilesAndDirs.File): Document.WithContent {
        return Document.WithContent(
            Document(
                file.getPath(),
                file.readModificationTime()
            ),
            file.readText()
        )
    }
}