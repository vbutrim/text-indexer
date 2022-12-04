package com.vbutrim.file

import java.time.Instant

fun java.io.File.readModificationTime(): Instant = Instant.ofEpochMilli(this.lastModified())