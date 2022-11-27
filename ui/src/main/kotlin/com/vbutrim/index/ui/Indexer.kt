package com.vbutrim.index.ui

import kotlinx.coroutines.*
import java.awt.event.ActionListener
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

interface Indexer : CoroutineScope {

    val job: Job;

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    fun init() {
        addOnWindowClosingListener {
            job.cancel()
            System.exit(0)
        }
    }

    fun addOnWindowClosingListener(listener: () -> Unit)
}