package org.megras.util.extensions

import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.Base64

fun ByteArray.toHex(): String = joinToString(separator = "") { b -> "%02x".format(b) }.trim()

fun ByteArray.toBase32(): String = Base32(true).encodeToString(this).trim()

fun ByteArray.toBase64(): String = Base64(true).encodeToString(this).trim()