package com.vbutrim.file

import java.util.*

class RandomStringGenerator {
    private val random = Random()

    companion object {
        private const val ALPHABET = "0123456789abcdefjhijklmnopqrstuvwxyzABCDEFJHIJKLMNOPQRSTUVWXYZ"
        private const val LENGTH = 15

        fun nextString(): String {
            return RandomStringGenerator()
                .nextString()
        }
    }

    fun nextString(): String {
        val sb = StringBuilder(LENGTH)
        for (i in 0 until LENGTH) {
            sb.append(nextChar())
        }
        return sb.toString()
    }

    private fun nextChar(): Char {
        return ALPHABET[nextInt()]
    }

    private fun nextInt(): Int {
        return random.nextInt(ALPHABET.length)
    }
}