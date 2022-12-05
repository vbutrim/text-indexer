package com.vbutrim.file

import com.vbutrim.fightClubAbsolutePath
import com.vbutrim.resourcesDirectory
import com.vbutrim.starWarsAbsolutePath
import com.vbutrim.textsDirectoryPath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class FileManagerTest {

    @Test
    fun shouldSplitOnFilesAndDirs() {
        // Given
        val tempFile = resourcesDirectory.child("temp_file")
        require(!tempFile.exists())

        try {
            tempFile.createNewFile()

            // When
            val result = FileManager.splitOnFilesAndDirs(
                listOf(tempFile.toPath(), textsDirectoryPath).map { AbsolutePath.cons(it) }
            )

            // Then
            Assertions.assertEquals(listOf(AbsolutePath.cons(tempFile.toPath())), result.files.map { it.getPath() })
            Assertions.assertEquals(listOf(AbsolutePath.cons(textsDirectoryPath)), result.dirs.map { it.path })
            Assertions.assertEquals(
                listOf(fightClubAbsolutePath, starWarsAbsolutePath),
                result.dirs.flatMap { it.files.map { it.getPath() } }
            )
        } finally {
            tempFile.delete()
        }
    }
}