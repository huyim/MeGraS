package org.megras.util

import org.megras.util.extensions.toBase32
import org.megras.util.extensions.toBase64
import org.megras.util.extensions.toHex
import java.io.InputStream
import java.security.MessageDigest

object HashUtil {

    private const val SHA3_512 = "SHA3-256"
    private const val STREAM_BUFFER_LENGTH = 512

    private fun digest() = MessageDigest.getInstance(SHA3_512)

    private fun updateDigest(digest: MessageDigest, data: InputStream): MessageDigest {
        val buffer = ByteArray(STREAM_BUFFER_LENGTH)
        var read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)
        while (read > -1) {
            digest.update(buffer, 0, read)
            read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)
        }
        return digest
    }

    fun hash(stream: InputStream): ByteArray = updateDigest(digest(), stream).digest()

    fun hashToHex(stream: InputStream): String = hash(stream).toHex()

    fun hashToBase32(stream: InputStream): String = hash(stream).toBase32()

    fun hashToBase64(stream: InputStream): String = hash(stream).toBase64()

}