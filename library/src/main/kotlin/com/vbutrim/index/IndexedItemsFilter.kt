package com.vbutrim.index

enum class IndexedItemsFilter {
    ANY,
    SOURCES_ONLY
    ;

    fun isAny(): Boolean {
        return when(this) {
            ANY -> true
            SOURCES_ONLY -> false
        }
    }
}