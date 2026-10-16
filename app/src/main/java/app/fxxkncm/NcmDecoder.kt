package app.fxxkncm

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming decoder for the common NetEase NCM container.
 *
 * This implementation deliberately keeps the container logic independent from Android UI.
 * Validate against real-world NCM variants before treating it as production-ready.
 */
object NcmDecoder {
    private val magic = byteArrayOf(0x43, 0x54, 0x45, 0x4e, 0x46, 0x44, 0x41, 0x4d)
    private val coreKey = byteArrayOf(
        0x68, 0x7a, 0x48, 0x52, 0x41, 0x6d, 0x73, 0x6f,
        0x35, 0x6b, 0x49, 0x6e, 0x62, 0x61, 0x78, 0x57
    )
    private val metaKey = byteArrayOf(
        0x23, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x54, 0x51,
        0x6e, 0x5a, 0x26, 0x36, 0x65, 0x3c, 0x6c, 0x6a
    )

    data class Result(val extension: String, val metadata: String?)

    fun decode(input: InputStream, output: OutputStream): Result {
        val source = DataInputStream(BufferedInputStream(input))
        val header = ByteArray(8)
        source.readFully(header)
        if (!header.contentEquals(magic)) {
            throw IllegalArgumentException("不是有效的 NCM 文件")
        }

        // Two-byte container version/reserved field.
        readExact(source, 2)
        val metadataLength = readLittleEndianInt(source)
        if (metadataLength < 0 || metadataLength > MAX_METADATA_BYTES) {
            throw IllegalArgumentException("NCM 元数据长度异常")
        }
        val encryptedMetadata = readExact(source, metadataLength)
        for (i in encryptedMetadata.indices) encryptedMetadata[i] = (encryptedMetadata[i].toInt() xor 0x64).toByte()
        val metadataBytes = decryptAes(encryptedMetadata, metaKey)
        val metadata = metadataBytes.toString(Charsets.UTF_8).removePrefix("music:")

        // CRC32 and reserved fields are not needed for audio decoding.
        readExact(source, 4)
        readExact(source, 5)

        val imageLength = readLittleEndianInt(source)
        if (imageLength < 0 || imageLength > MAX_IMAGE_BYTES) {
            throw IllegalArgumentException("NCM 封面数据长度异常")
        }
        skipFully(source, imageLength.toLong())

        val keyLength = readLittleEndianInt(source)
        if (keyLength < 0 || keyLength > MAX_KEY_BYTES) {
            throw IllegalArgumentException("NCM 音频密钥长度异常")
        }
        val encryptedKey = readExact(source, keyLength)
        for (i in encryptedKey.indices) encryptedKey[i] = (encryptedKey[i].toInt() xor 0x64).toByte()
        val decryptedKey = decryptAes(encryptedKey, coreKey)
        val keyMaterial = decryptedKey.copyOfRange(minOf(17, decryptedKey.size), decryptedKey.size)
        for (i in keyMaterial.indices) keyMaterial[i] = (keyMaterial[i].toInt() xor 0x63).toByte()
        if (keyMaterial.isEmpty()) throw IllegalArgumentException("NCM 音频密钥为空")

        val keyBox = createKeyBox(keyMaterial)
        val buffer = ByteArray(BUFFER_SIZE)
        var extension = if (metadata.contains("\"format\"") && metadata.contains("flac", ignoreCase = true)) "flac" else "mp3"
        var firstChunk = true
        var audioOffset = 0L
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            for (i in 0 until count) {
                // Use the absolute audio offset so short reads from document providers
                // do not reset the NCM keystream at arbitrary chunk boundaries.
                val position = ((audioOffset + i + 1L) and 0xff).toInt()
                val keyByte = keyBox[position].toInt() and 0xff
                val index = keyByte + (keyBox[(keyByte + position) and 0xff].toInt() and 0xff)
                buffer[i] = (buffer[i].toInt() xor (keyBox[index and 0xff].toInt() and 0xff)).toByte()
            }
            audioOffset += count
            if (firstChunk) {
                if (count >= 4 && buffer[0] == 'f'.code.toByte() && buffer[1] == 'L'.code.toByte() &&
                    buffer[2] == 'a'.code.toByte() && buffer[3] == 'C'.code.toByte()) {
                    extension = "flac"
                } else if (count >= 3 && buffer[0] == 'I'.code.toByte() &&
                    buffer[1] == 'D'.code.toByte() && buffer[2] == '3'.code.toByte()) {
                    extension = "mp3"
                }
                firstChunk = false
            }
            output.write(buffer, 0, count)
        }
        output.flush()
        return Result(extension, metadata.ifBlank { null })
    }

    private fun createKeyBox(keyMaterial: ByteArray): ByteArray {
        val box = ByteArray(256) { it.toByte() }
        var lastByte = 0
        var keyIndex = 0
        for (i in 0 until 256) {
            val current = box[i].toInt() and 0xff
            val key = keyMaterial[keyIndex].toInt() and 0xff
            val swapIndex = (current + lastByte + key) and 0xff
            val temp = box[i]
            box[i] = box[swapIndex]
            box[swapIndex] = temp
            lastByte = swapIndex
            keyIndex = (keyIndex + 1) % keyMaterial.size
        }
        return box
    }

    private fun decryptAes(data: ByteArray, key: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun readLittleEndianInt(input: InputStream): Int {
        val bytes = readExact(input, 4)
        return (bytes[0].toInt() and 0xff) or
            ((bytes[1].toInt() and 0xff) shl 8) or
            ((bytes[2].toInt() and 0xff) shl 16) or
            ((bytes[3].toInt() and 0xff) shl 24)
    }

    private fun readExact(input: InputStream, length: Int): ByteArray {
        if (length < 0) throw IllegalArgumentException("负数长度")
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(result, offset, length - offset)
            if (count < 0) throw EOFException("NCM 文件已截断")
            if (count == 0) continue
            offset += count
        }
        return result
    }

    private fun skipFully(input: InputStream, amount: Long) {
        var remaining = amount
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (input.read() < 0) throw EOFException("NCM 封面数据已截断")
                remaining--
            }
        }
    }

    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_METADATA_BYTES = 16 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024
    private const val MAX_KEY_BYTES = 1024 * 1024
}
