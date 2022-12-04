package com.vbutrim.index.ui

fun main() {
    setDefaultFontSize(18f)
    IndexerUI().apply {
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }
}