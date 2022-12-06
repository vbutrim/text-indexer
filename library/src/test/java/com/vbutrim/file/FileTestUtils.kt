package com.vbutrim.file

import java.io.File

/**
 * Not to have this code in production, as 'suspend' means that coroutine can freeze,
 * which can lead to that situation, where temp dir isn't deleted.
 */
suspend fun <R> withNewTempDirSuspendable(func: suspend (tempDir: File) -> R): R {
    return withNewTempDirSuspendable("text-indexer-test", func)
}

suspend fun <R> withNewTempDirSuspendable(prefix: String, func: suspend (tempDir: File) -> R): R {
    val tempDir: File = createNewTmpDir(prefix)

    return try {
        func.invoke(tempDir)
    } finally {
        tempDir.deleteRecursive()
    }
}