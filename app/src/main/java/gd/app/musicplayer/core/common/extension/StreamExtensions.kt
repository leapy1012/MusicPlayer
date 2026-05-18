package gd.app.musicplayer.core.common.extension

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

fun InputStream.readExact(size: Int): ByteArray {
    val buffer = ByteArray(size)
    readFully(buffer, 0, size)
    return buffer
}

fun InputStream.readFully(
    buffer: ByteArray,
    offset: Int,
    length: Int
) {
    var totalRead = 0

    while (totalRead < length) {
        val read = read(buffer, offset + totalRead, length - totalRead)
        if (read < 0) {
            throw EOFException()
        }

        totalRead += read
    }
}

fun InputStream.skipFully(byteCount: Long) {
    var remaining = byteCount

    while (remaining > 0L) {
        val skipped = skip(remaining)

        if (skipped <= 0L) {
            if (read() == -1) {
                throw EOFException()
            }

            remaining--
        } else {
            remaining -= skipped
        }
    }
}

fun copyExactly(
    inputStream: InputStream,
    outputStream: OutputStream,
    byteCount: Int
) {
    val buffer = ByteArray(1024)
    var remaining = byteCount

    while (remaining > 0) {
        val toRead = minOf(buffer.size, remaining)
        inputStream.readFully(buffer, 0, toRead)
        outputStream.write(buffer, 0, toRead)
        remaining -= toRead
    }
}

fun ByteArray.readLittleEndianInt(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}

fun ByteArray.readLittleEndianShort(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)
}

fun OutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

fun OutputStream.writeLittleEndianInt(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    )
}

fun OutputStream.writeLittleEndianShort(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    )
}