package com.vbutrim.file

import java.nio.file.Path

fun Path.child(child: String): Path {
    return this.resolve(child)
}