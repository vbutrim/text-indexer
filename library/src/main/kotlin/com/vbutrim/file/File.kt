package com.vbutrim.file

import java.io.File
import java.time.Instant

fun File.readModificationTime(): Instant = Instant.ofEpochMilli(this.lastModified())

fun File.asAbsolutePath(): AbsolutePath {
    return this.toPath().asAbsolutePath()
}

fun <R> withNewTempDir(func: (tempDir: File) -> R): R {
    return withNewTempDir("text-indexer", func)
}

fun <R> withNewTempDir(prefix: String, func: (tempDir: File) -> R): R {
    val tempDir: File = createNewTmpDir(prefix)

    return try {
        func.invoke(tempDir)
    } finally {
        tempDir.deleteRecursive()
    }
}

fun File.deleteRecursive() {
    for (file in this.safeListFiles()) {
        file.deleteRecursive()
    }
    this.delete()
}

fun File.safeListFiles(): List<File> {
    return this.listFiles()?.toList() ?: listOf()
}

fun File.child(child: String): File {
    return File(this, child)
}

fun createNewTmpDir(prefix: String): File {
    val file: File = tmpDir().child(prefix + "-" + System.currentTimeMillis() + RandomStringGenerator.nextString())
    file.mkdirs()
    require (file.isDirectory) {
        "failed to create temp dir $file"
    }
    return file
}

private fun tmpDir(): File {
    return File(System.getProperty("java.io.tmpdir"))
}
