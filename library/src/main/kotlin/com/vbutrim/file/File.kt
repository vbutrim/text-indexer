package com.vbutrim.file

import java.io.File
import java.time.Instant
import java.util.*

fun File.readModificationTime(): Instant = Instant.ofEpochMilli(this.lastModified())

fun <R> withNewTempDir(func: (dir: File) -> R): R {
    return withNewTempDir("text-indexer", func)
}

fun <R> withNewTempDir(prefix: String, func: (dir: File) -> R): R {
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
    val file: File = tmpDir().child(prefix + "-" + System.currentTimeMillis() + RandomStringGenerator.next())
    file.mkdirs()
    require (file.isDirectory) {
        "failed to create temp dir $file"
    }
    return file
}

private fun tmpDir(): File {
    return File(System.getProperty("java.io.tmpdir"))
}

private class RandomStringGenerator {
    private val random = Random()

    companion object {
        private const val ALPHABET = "0123456789abcdefjhijklmnopqrstuvwxyzABCDEFJHIJKLMNOPQRSTUVWXYZ"
        private const val LENGTH = 15

        fun next(): String {
            return RandomStringGenerator()
                .next()
        }
    }

    private fun next(): String {
        val sb = StringBuilder(LENGTH)
        for (i in 0 until LENGTH) {
            sb.append(nextChar())
        }
        return sb.toString()
    }

    private fun nextChar(): Char {
        return ALPHABET[nextInt()]
    }

    fun nextInt(): Int {
        return random.nextInt(ALPHABET.length)
    }
}