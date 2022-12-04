package com.vbutrim.index.ui

import com.vbutrim.index.DocumentTokenizer
import com.vbutrim.index.DocumentsIndexer

fun main() {
    val documentsIndexer = DocumentsIndexer(
        DocumentTokenizer.BasedOnWordSeparation()
    )

    setDefaultFontSize(18f)
    IndexerUI(documentsIndexer).apply {
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }
}