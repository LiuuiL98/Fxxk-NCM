package app.fxxkncm

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming decoder for the NetEase NCM container.
 *
 * Layout: magic + version/reserved + encrypted audio key + encrypted metadata
 * + CRC/reserved + cover image + encrypted audio stream.
 */
object NcmDecoder {
    private val magic = byteArrayOf(0x43, 0x54, 0x45, 0x4e, 0x46, 0x44, 0x41, 0x4d)
    private val coreKey = byteArrayOf(
        0x68, 0x7a, 0x48, 0x52, 0x41, 0x6d, 0x73, 0x6f,
        0x35, 0x6b, 0x49, 0x6e, 0x62, 0x61, 0x78, 0x57
    )
    private val metaKey = byteArrayOf(
        0x23, 0x31, 0x34, 0x6c, 0x6a, 0x6b, 0x5f, 0x21,
        0x5c, 0x5d, 0x26, 0x30, 0x55, 0x3c, 0x27, 0x28
    )

    data class Result(val extension: String, val metadata: String?)

    fun decode(input: InputStream, output: OutputStream): Result {
        val source = DataInputStream(BufferedInputStream(input))
        val header = ByteArray(8)
        source.readFully(header)
        if (!header.contentEquals(magic)) {
            throw IllegalArgumentException("不是有效的 NCM 文件")
        }

        // Two-byte version/reserved field.
        readExact(source, 2)

        // Audio key: XOR 0x64, AES-ECB decrypt, remove the 17-byte prefix.
        val keyLength = readLength(source, MAX_KEY_BYTES, "音频密钥")
        val encryptedKey = xorBytes(readExact(source, keyLength), 0x64)
        if (encryptedKey.isEmpty() || encryptedKey.size % AES_BLOCK_SIZE != 0) {
            throw IllegalArgumentException("NCM 音频密钥密文长度无效")
        }
        val decryptedKey = decryptAes(encryptedKey, coreKey, "音频密钥")
        val keyPrefix = "neteasecloudmusic".toByteArray(Charsets.US_ASCII)
        if (decryptedKey.size <= keyPrefix.size ||
            !decryptedKey.copyOfRange(0, keyPrefix.size).contentEquals(keyPrefix)) {
            throw IllegalArgumentException("NCM 音频密钥前缀不正确，文件可能损坏或格式不受支持")
        }
        // Important: the audio key bytes are NOT XORed with 0x63.
        val keyMaterial = decryptedKey.copyOfRange(keyPrefix.size, decryptedKey.size)
        if (keyMaterial.isEmpty()) throw IllegalArgumentException("NCM 音频密钥为空")

        // Metadata: XOR 0x63, strip the 22-byte ASCII prefix, Base64 decode, AES decrypt.
        val metadataLength = readLength(source, MAX_METADATA_BYTES, "元数据")
        val metadataField = readExact(source, metadataLength)
        val metadata = if (metadataField.isEmpty()) {
            ""
        } else {
            val unobfuscated = xorBytes(metadataField, 0x63)
            val encryptedMetadata = decodeBase64Field(unobfuscated, METADATA_PREFIX_LENGTH, "元数据")
            if (encryptedMetadata.isEmpty() || encryptedMetadata.size % AES_BLOCK_SIZE != 0) {
                throw IllegalArgumentException("NCM 元数据 AES 密文长度无效")
            }
            val plain = decryptAes(encryptedMetadata, metaKey, "元数据")
            val text = plain.toString(Charsets.UTF_8)
            if (!text.startsWith("music:")) {
                throw IllegalArgumentException("NCM 元数据前缀不正确，文件可能损坏或格式不受支持")
            }
            text.removePrefix("music:")
        }

        // CRC32 (4 bytes) and reserved bytes (5 bytes).
        readExact(source, 4)
        readExact(source, 5)

        val imageLength = readLength(source, MAX_IMAGE_BYTES, "封面数据")
        skipFully(source, imageLength.toLong())

        val keyBox = createKeyBox(keyMaterial)
        val audioMask = createAudioMask(keyBox)
        var extension = if (metadata.contains("\"format\"") &&
            metadata.contains("flac", ignoreCase = true)) "flac" else "mp3"
        val buffer = ByteArray(BUFFER_SIZE)
        var firstChunk = true
        var audioOffset = 0L
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (count == 0) continue

            // The NCM mask repeats every 256 bytes. Precompute it once to keep
            // the hot loop to an XOR and a small, wraparound index increment.
            var maskIndex = ((audioOffset + 1L) and 0xff).toInt()
            for (i in 0 until count) {
                buffer[i] = (buffer[i].toInt() xor audioMask[maskIndex].toInt()).toByte()
                maskIndex = (maskIndex + 1) and 0xff
            }
            audioOffset += count

            if (firstChunk) {
                if (count >= 4 && buffer[0] == 'f'.code.toByte() &&
                    buffer[1] == 'L'.code.toByte() && buffer[2] == 'a'.code.toByte() &&
                    buffer[3] == 'C'.code.toByte()) {
                    extension = "flac"
                } else if (count >= 3 && buffer[0] == 'I'.code.toByte() &&
                    buffer[1] == 'D'.code.toByte() && buffer[2] == '3'.code.toByte()) {
                    extension = "mp3"
                } else if (count >= 2 && (buffer[0].toInt() and 0xff) == 0xff &&
                    (buffer[1].toInt() and 0xe0) == 0xe0) {
                    extension = "mp3"
                } else {
                    throw IllegalArgumentException("NCM 音频解密后文件头无法识别，密钥或文件结构可能异常")
                }
                firstChunk = false
            }
            output.write(buffer, 0, count)
        }
        if (audioOffset == 0L) throw IllegalArgumentException("NCM 文件不包含音频数据")
        output.flush()
        return Result(extension, metadata.ifBlank { null })
    }

    private fun createKeyBox(keyMaterial: ByteArray): ByteArray {
        val box = ByteArray(256) { it.toByte() }
        var lastByte = 0
        var keyIndex = 0
        for (i in 0 until 256) {
            val swap = box[i].toInt() and 0xff
            val swapIndex = (swap + lastByte + (keyMaterial[keyIndex].toInt() and 0xff)) and 0xff
            box[i] = box[swapIndex].also { box[swapIndex] = box[i] }
            lastByte = swapIndex
            keyIndex = (keyIndex + 1) % keyMaterial.size
        }
        return box
    }

    internal fun createAudioMask(keyBox: ByteArray): ByteArray = ByteArray(256) { position ->
        val boxValue = keyBox[position].toInt() and 0xff
        val second = keyBox[(boxValue + position) and 0xff].toInt() and 0xff
        keyBox[(boxValue + second) and 0xff]
    }

    private fun xorBytes(data: ByteArray, mask: Int): ByteArray =
        ByteArray(data.size) { i -> (data[i].toInt() xor mask).toByte() }

    private fun decodeBase64Field(data: ByteArray, prefixLength: Int, label: String): ByteArray {
        if (data.size <= prefixLength) {
            throw IllegalArgumentException("NCM $label 数据长度不足")
        }
        val prefix = data.copyOfRange(0, prefixLength).toString(Charsets.US_ASCII)
        if (prefix != METADATA_PREFIX) {
            throw IllegalArgumentException("NCM $label 前缀不正确，文件可能损坏或格式不受支持")
        }
        return try {
            Base64.getDecoder().decode(data.copyOfRange(prefixLength, data.size))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("NCM $label Base64 数据无效，文件可能损坏或格式不受支持", e)
        }
    }

    private fun decryptAes(data: ByteArray, key: ByteArray, label: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return try {
            cipher.doFinal(data)
        } catch (e: BadPaddingException) {
            throw IllegalArgumentException("NCM $label AES 解密失败：密钥或加密数据不匹配", e)
        }
    }

    private fun readLength(input: InputStream, max: Int, label: String): Int {
        val value = readLittleEndianInt(input)
        if (value < 0 || value > max) throw IllegalArgumentException("NCM ${label}长度异常")
        return value
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
    private const val METADATA_PREFIX = "163 key(Don't modify):"
    private const val AES_BLOCK_SIZE = 16
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_METADATA_BYTES = 16 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024
    private const val MAX_KEY_BYTES = 1024 * 1024
}
