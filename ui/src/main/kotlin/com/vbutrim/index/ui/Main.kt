package com.vbutrim.index.ui

import com.vbutrim.index.DocumentsIndexer
import java.nio.file.Path

suspend fun main() {
    // DocumentsIndexer.updateWith(Path.of("C:\\work\\TextIndexer\\texts\\C. Palahniuk 'Fight club'"))

    setDefaultFontSize(18f)
    IndexerUI().apply {
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }
}