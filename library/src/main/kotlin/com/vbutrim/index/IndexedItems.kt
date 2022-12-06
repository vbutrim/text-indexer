package com.vbutrim.index

fun List<IndexedItem>.flatMappedFilesInDirs(): List<IndexedItem> {
    return this.flatMap {
        when (it) {
            is IndexedItem.File -> {
                listOf(it)
            }
            is IndexedItem.Dir -> {
                listOf(IndexedItem.Dir(it.path, this.flatMappedFiles()))
            }
        }
    }
}

fun List<IndexedItem>.flatMappedFiles(): List<IndexedItem> {
    return this.flatMap {
        when (it) {
            is IndexedItem.File -> {
                listOf(it)
            }
            is IndexedItem.Dir -> {
                this.flatMappedFiles()
            }
        }
    }
}