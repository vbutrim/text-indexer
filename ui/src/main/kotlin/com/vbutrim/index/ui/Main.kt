package com.vbutrim.index.ui

import com.vbutrim.index.DocumentTokenizer
import com.vbutrim.index.DocumentsIndexer
import java.time.Duration

fun main() {
    val documentsIndexer = DocumentsIndexer(
        DocumentTokenizer.BasedOnWordSeparation()
    )

    setDefaultFontSize(18f)
    IndexerUI(documentsIndexer, Duration.ofSeconds(10), false)
        .apply {
            pack()
            setLocationRelativeTo(null)
            isVisible = true
        }
}