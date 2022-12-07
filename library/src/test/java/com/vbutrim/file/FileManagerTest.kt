package com.vbutrim.file

import com.vbutrim.fightClubAbsolutePath
import com.vbutrim.practicalGuideToHappinessAbsolutePath
import com.vbutrim.starWarsAbsolutePath
import com.vbutrim.textsDirectoryPath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class FileManagerTest {

    @Test
    fun shouldSplitOnFilesAndDirs() {
        withNewTempDir { tempDir ->
            // Given
            val tempFile = tempDir.child("temp_file")
            tempFile.createNewFile()

            // When
            val result = FileManager.splitOnFilesAndDirs(
                listOf(tempFile.toPath(), textsDirectoryPath).map { it.asAbsolutePath() }
            )

            // Then
            Assertions.assertEquals(listOf(tempFile.asAbsolutePath()), result.files.map { it.getPath() })
            Assertions.assertEquals(listOf(textsDirectoryPath.asAbsolutePath()), result.dirs.map { it.path })
            Assertions.assertEquals(
                listOf(fightClubAbsolutePath, practicalGuideToHappinessAbsolutePath, starWarsAbsolutePath),
                result.dirs.flatMap { dir -> dir.files.map { it.getPath() } }
            )
        }
    }
}