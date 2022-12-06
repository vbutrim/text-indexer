package com.vbutrim.index.ui

import com.vbutrim.index.DocumentTokenizer
import com.vbutrim.index.DocumentsIndexer
import java.time.Duration

fun main(args: Array<String>) {
    val parsedArgs = Args.parse(args)

    val documentsIndexer = DocumentsIndexer(
        DocumentTokenizer.BasedOnWordSeparation()
    )

    setDefaultFontSize(18f)
    IndexerUI(
        documentsIndexer,
        Duration.ofSeconds(parsedArgs.getLongValue(Args.Arg.Type.SYNC_DELAY_TIME_IN_SECONDS) ?: 10),
        parsedArgs.getBooleanValue(Args.Arg.Type.DEBUG_PANEL_IS_ENABLED) ?: false
    )
    .apply {
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }
}