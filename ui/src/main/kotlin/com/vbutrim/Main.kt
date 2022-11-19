package com.vbutrim

import com.vbutrim.index.IndexUpdater
import java.nio.file.Path

fun main() {
    IndexUpdater().build(Path.of("C:\\work\\TextIndexer\\texts\\C. Palahniuk 'Fight club'"));
}