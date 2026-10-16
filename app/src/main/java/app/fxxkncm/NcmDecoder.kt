package app.fxxkncm

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming decoder for the common NetEase NCM container.
 *
 * NCM metadata and key fields are XOR-obfuscated, prefixed, and Base64-encoded
 * before AES decryption. Keep this implementation independent from Android UI.
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
        val metadataField = readExact(source, metadataLength)
        val metadata = if (metadataField.isEmpty()) {
            ""
        } else {
            val unobfuscated = xorBytes(metadataField, 0x64)
            val encrypted = decodeBase64Field(unobfuscated, METADATA_PREFIX_LENGTH, "元数据")
            val plain = decryptAes(encrypted, metaKey)
            val text = plain.toString(Charsets.UTF_8)
            if (!text.startsWith("music:")) {
                throw IllegalArgumentException("NCM 元数据前缀不正确，文件可能损坏或格式不受支持")
            }
            text.removePrefix("music:")
        }

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
        val keyField = readExact(source, keyLength)
        if (keyField.isEmpty()) throw IllegalArgumentException("NCM 音频密钥为空")
        val unobfuscatedKey = xorBytes(keyField, 0x64)
        val encryptedKey = decodeBase64Field(unobfuscatedKey, KEY_PREFIX_LENGTH, "音频密钥")
        val decryptedKey = decryptAes(encryptedKey, coreKey)
        val keyPrefix = "neteasecloudmusic".toByteArray(Charsets.UTF_8)
        if (decryptedKey.size <= keyPrefix.size ||
            !decryptedKey.copyOfRange(0, keyPrefix.size).contentEquals(keyPrefix)) {
            throw IllegalArgumentException("NCM 音频密钥前缀不正确，文件可能损坏或格式不受支持")
        }
        val keyMaterial = xorBytes(decryptedKey.copyOfRange(keyPrefix.size, decryptedKey.size), 0x63)
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

    private fun xorBytes(data: ByteArray, mask: Int): ByteArray =
        ByteArray(data.size) { i -> (data[i].toInt() xor mask).toByte() }

    private fun decodeBase64Field(data: ByteArray, prefixLength: Int, label: String): ByteArray {
        if (data.size <= prefixLength) {
            throw IllegalArgumentException("NCM $label 数据长度不足")
        }
        val encoded = data.copyOfRange(prefixLength, data.size)
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("NCM $label Base64 数据无效，文件可能损坏或格式不受支持", e)
        }
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
        if (data.isEmpty() || data.size % AES_BLOCK_SIZE != 0) {
            throw IllegalArgumentException("NCM AES 密文长度无效")
        }
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return try {
            cipher.doFinal(data)
        } catch (e: javax.crypto.BadPaddingException) {
            throw IllegalArgumentException("NCM AES 解密失败：密钥或加密数据不匹配，文件可能损坏或格式不受支持", e)
        }
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

    private const val METADATA_PREFIX_LENGTH = 22
    private const val KEY_PREFIX_LENGTH = 17
    private const val AES_BLOCK_SIZE = 16
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_METADATA_BYTES = 16 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024
    private const val MAX_KEY_BYTES = 1024 * 1024
}
