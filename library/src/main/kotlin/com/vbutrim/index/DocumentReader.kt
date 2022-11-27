package com.vbutrim.index

import java.io.File
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.pathString

object DocumentReader {
    fun read(path: Path): Document.WithContent {
        val file = File(path.pathString)
        return Document.WithContent(
            Document(
                path,
                FileExplorer.getModificationTime(file)
            ),
            file.readText()
        )
    }
}