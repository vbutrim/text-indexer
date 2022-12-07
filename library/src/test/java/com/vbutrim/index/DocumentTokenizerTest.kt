package com.vbutrim.index

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class DocumentTokenizerTest {
    @Test
    fun shouldCollectTokensBasedOnWordSeparation() {
        // Given
        val content = "\"This isn't really death,\" Tyler says. \"We'll be legend. We won't grow old.\""

        // When
        val result = DocumentTokenizer.BasedOnWordSeparation()
            .collectTokens(content)
            .toArray()

        // Then
        Assertions.assertArrayEquals(
            arrayOf(
                "this",
                "isn",
                "t",
                "really",
                "death",
                "tyler",
                "says",
                "we",
                "ll",
                "be",
                "legend",
                "we",
                "won",
                "t",
                "grow",
                "old"
            ),
            result
        )
    }
}