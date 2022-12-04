package com.vbutrim.index

enum class IndexedItemsFilter {
    ANY,
    MARKED_AS_SOURCES_ONLY
    ;

    fun isAny(): Boolean {
        return when(this) {
            ANY -> true
            MARKED_AS_SOURCES_ONLY -> false
        }
    }
}