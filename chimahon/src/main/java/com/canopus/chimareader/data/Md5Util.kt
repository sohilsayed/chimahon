package com.canopus.chimareader.data

import java.security.MessageDigest

fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5")
    val bytes = digest.digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
