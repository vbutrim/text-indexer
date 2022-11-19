package com.vbutrim.index

import java.io.File
import java.time.Instant

class FileExplorer {
    companion object {
        fun getModificationTime(file: File): Instant {
            return Instant.ofEpochMilli(file.lastModified())
        }
    }
}