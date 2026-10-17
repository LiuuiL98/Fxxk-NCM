package app.fxxkncm

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * Tags stored alongside the audio stream in the NCM container.
 * The NCM cover is capped by the decoder before being handed to this writer.
 */
internal data class NcmAudioTags(
    val title: String?,
    val artists: List<String>,
    val album: String?,
    val cover: ByteArray?,
    val coverMime: String?
) {
    val hasTextTags: Boolean
        get() = !title.isNullOrBlank() || artists.isNotEmpty() || !album.isNullOrBlank()

    val hasAnyTags: Boolean
        get() = hasTextTags || (cover != null && cover.isNotEmpty())
}

internal fun parseNcmAudioTags(metadataJson: String?, coverBytes: ByteArray): NcmAudioTags? {
    val root = metadataJson?.takeIf { it.isNotBlank() }?.let {
        runCatching { JSONObject(it) }.getOrNull()
    } ?: return null

    val title = root.optString("musicName", "").takeIf { it.isNotBlank() && it != "null" }
    val album = root.optString("album", "").takeIf { it.isNotBlank() && it != "null" }
    val artists = buildList {
        val source = root.optJSONArray("artist")
        if (source != null) {
            for (i in 0 until source.length()) {
                val item = source.opt(i)
                val name = when (item) {
                    is org.json.JSONArray -> item.optString(0, "")
                    is String -> item
                    else -> ""
                }.trim()
                if (name.isNotEmpty() && name != "null") add(name)
            }
        }
    }.distinct()

    val mime = when {
        coverBytes.size >= 3 && coverBytes[0] == 0xFF.toByte() && coverBytes[1] == 0xD8.toByte() &&
            coverBytes[2] == 0xFF.toByte() -> "image/jpeg"
        coverBytes.size >= 8 && coverBytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        ) -> "image/png"
        else -> null
    }
    val cover = coverBytes.takeIf { it.isNotEmpty() && mime != null }
    val tags = NcmAudioTags(title, artists, album, cover, mime)
    return tags.takeIf { it.hasAnyTags }
}

/**
 * Rebuilds the FLAC metadata header while streaming the original audio:
 * existing STREAMINFO/SEEKTABLE/etc. blocks are retained, existing Vorbis
 * Comment and Picture blocks are replaced, then NCM title/artist/album/cover
 * are written as standards-compliant FLAC metadata blocks.
 */
internal class FlacMetadataOutputStream(
    private val target: OutputStream,
    private val tags: NcmAudioTags
) : OutputStream() {
    private var phase = PHASE_MARKER
    private val marker = ByteArray(4)
    private var markerCount = 0
    private val blockHeader = ByteArray(4)
    private var blockHeaderCount = 0
    private var remaining = 0
    private var skipPayload = false
    private var isLastInputBlock = false
    private var finished = false

    override fun write(value: Int) {
        write(byteArrayOf(value.toByte()), 0, 1)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (finished) throw IOException("FLAC metadata writer is already finished")
        if (offset < 0 || length < 0 || offset + length > bytes.size) {
            throw IndexOutOfBoundsException()
        }
        var position = offset
        val end = offset + length
        while (position < end) {
            when (phase) {
                PHASE_MARKER -> {
                    val count = minOf(4 - markerCount, end - position)
                    System.arraycopy(bytes, position, marker, markerCount, count)
                    markerCount += count
                    position += count
                    if (markerCount == 4) {
                        target.write(marker)
                        if (!marker.contentEquals(FLAC_MARKER)) {
                            phase = PHASE_AUDIO
                        } else {
                            phase = PHASE_BLOCK_HEADER
                        }
                    }
                }
                PHASE_BLOCK_HEADER -> {
                    val count = minOf(4 - blockHeaderCount, end - position)
                    System.arraycopy(bytes, position, blockHeader, blockHeaderCount, count)
                    blockHeaderCount += count
                    position += count
                    if (blockHeaderCount == 4) {
                        isLastInputBlock = (blockHeader[0].toInt() and 0x80) != 0
                        val type = blockHeader[0].toInt() and 0x7F
                        remaining = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                            ((blockHeader[2].toInt() and 0xFF) shl 8) or
                            (blockHeader[3].toInt() and 0xFF)
                        skipPayload = type == TYPE_VORBIS_COMMENT || type == TYPE_PICTURE
                        if (!skipPayload) {
                            blockHeader[0] = type.toByte() // All old blocks must no longer be the final block.
                            target.write(blockHeader)
                        }
                        blockHeaderCount = 0
                        phase = PHASE_BLOCK_PAYLOAD
                        if (remaining == 0) finishInputBlock()
                    }
                }
                PHASE_BLOCK_PAYLOAD -> {
                    val count = minOf(remaining, end - position)
                    if (!skipPayload && count > 0) target.write(bytes, position, count)
                    position += count
                    remaining -= count
                    if (remaining == 0) finishInputBlock()
                }
                PHASE_AUDIO -> {
                    target.write(bytes, position, end - position)
                    position = end
                }
            }
        }
    }

    private fun finishInputBlock() {
        if (isLastInputBlock) {
            writeInjectedMetadata()
            phase = PHASE_AUDIO
        } else {
            phase = PHASE_BLOCK_HEADER
        }
    }

    private fun writeInjectedMetadata() {
        val comment = buildVorbisComment(tags)
        val picture = buildFlacPicture(tags)
        if (comment != null) writeFlacBlock(TYPE_VORBIS_COMMENT, comment, picture == null)
        if (picture != null) writeFlacBlock(TYPE_PICTURE, picture, true)
    }

    private fun writeFlacBlock(type: Int, payload: ByteArray, last: Boolean) {
        if (payload.size > 0xFFFFFF) throw IOException("FLAC metadata block is too large")
        target.write(((if (last) 0x80 else 0) or type) and 0xFF)
        target.write((payload.size ushr 16) and 0xFF)
        target.write((payload.size ushr 8) and 0xFF)
        target.write(payload.size and 0xFF)
        target.write(payload)
    }

    fun finish() {
        if (finished) return
        if (phase != PHASE_AUDIO) throw IOException("FLAC metadata header is incomplete")
        target.flush()
        finished = true
    }

    override fun flush() = target.flush()

    private companion object {
        val FLAC_MARKER = byteArrayOf(0x66, 0x4C, 0x61, 0x43)
        const val PHASE_MARKER = 0
        const val PHASE_BLOCK_HEADER = 1
        const val PHASE_BLOCK_PAYLOAD = 2
        const val PHASE_AUDIO = 3
        const val TYPE_VORBIS_COMMENT = 4
        const val TYPE_PICTURE = 6
    }
}

/** Adds a broadly compatible ID3v2.3 tag to a decoded MP3 stream. */
internal class Mp3MetadataOutputStream(
    private val target: OutputStream,
    private val tags: NcmAudioTags
) : OutputStream() {
    private var started = false
    private var finished = false

    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (finished) throw IOException("MP3 metadata writer is already finished")
        if (offset < 0 || length < 0 || offset + length > bytes.size) throw IndexOutOfBoundsException()
        if (!started) {
            target.write(buildId3v23Tag(tags))
            started = true
        }
        target.write(bytes, offset, length)
    }

    fun finish() {
        if (!started) target.write(buildId3v23Tag(tags))
        target.flush()
        finished = true
    }

    override fun flush() = target.flush()
}

private fun buildVorbisComment(tags: NcmAudioTags): ByteArray? {
    val comments = mutableListOf<String>()
    tags.title?.takeIf { it.isNotBlank() }?.let { comments += "TITLE=$it" }
    tags.artists.filter { it.isNotBlank() }.forEach { comments += "ARTIST=$it" }
    tags.album?.takeIf { it.isNotBlank() }?.let { comments += "ALBUM=$it" }
    if (comments.isEmpty()) return null

    val out = ByteArrayOutputStream()
    writeLeInt(out, VORBIS_VENDOR.toByteArray(Charsets.UTF_8).size)
    out.write(VORBIS_VENDOR.toByteArray(Charsets.UTF_8))
    writeLeInt(out, comments.size)
    comments.forEach {
        val bytes = it.toByteArray(Charsets.UTF_8)
        writeLeInt(out, bytes.size)
        out.write(bytes)
    }
    return out.toByteArray()
}

private fun buildFlacPicture(tags: NcmAudioTags): ByteArray? {
    val data = tags.cover ?: return null
    val mime = tags.coverMime ?: return null
    val dimensions = imageDimensions(data)
    val out = ByteArrayOutputStream()
    writeBeInt(out, 3) // Front cover.
    val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
    writeBeInt(out, mimeBytes.size)
    out.write(mimeBytes)
    writeBeInt(out, 0) // Empty description.
    writeBeInt(out, dimensions.first)
    writeBeInt(out, dimensions.second)
    writeBeInt(out, 0) // Unknown bit depth.
    writeBeInt(out, 0) // Unknown indexed colors.
    writeBeInt(out, data.size)
    out.write(data)
    return out.toByteArray()
}

private fun buildId3v23Tag(tags: NcmAudioTags): ByteArray {
    val frames = ByteArrayOutputStream()
    tags.title?.takeIf { it.isNotBlank() }?.let { writeId3TextFrame(frames, "TIT2", it) }
    tags.artists.takeIf { it.isNotEmpty() }?.let { writeId3TextFrame(frames, "TPE1", it.joinToString("; ")) }
    tags.album?.takeIf { it.isNotBlank() }?.let { writeId3TextFrame(frames, "TALB", it) }
    val cover = tags.cover
    val mime = tags.coverMime
    if (cover != null && mime != null) {
        val apic = ByteArrayOutputStream()
        apic.write(0) // ISO-8859-1 encoding for MIME and empty description.
        apic.write(mime.toByteArray(Charsets.US_ASCII))
        apic.write(0)
        apic.write(3) // Front cover.
        apic.write(0) // Empty description.
        apic.write(cover)
        writeId3Frame(frames, "APIC", apic.toByteArray())
    }
    val body = frames.toByteArray()
    val result = ByteArrayOutputStream()
    result.write(byteArrayOf(0x49, 0x44, 0x33, 3, 0, 0))
    result.write(syncSafe(body.size))
    result.write(body)
    return result.toByteArray()
}

private fun writeId3TextFrame(out: OutputStream, frameId: String, text: String) {
    val payload = byteArrayOf(1) + text.toByteArray(Charsets.UTF_16)
    writeId3Frame(out, frameId, payload)
}

private fun writeId3Frame(out: OutputStream, frameId: String, payload: ByteArray) {
    out.write(frameId.toByteArray(Charsets.US_ASCII))
    writeBeInt(out, payload.size)
    out.write(0)
    out.write(0)
    out.write(payload)
}

private fun syncSafe(value: Int): ByteArray = byteArrayOf(
    ((value ushr 21) and 0x7F).toByte(),
    ((value ushr 14) and 0x7F).toByte(),
    ((value ushr 7) and 0x7F).toByte(),
    (value and 0x7F).toByte()
)

private fun imageDimensions(data: ByteArray): Pair<Int, Int> {
    if (data.size >= 24 && data.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    )) {
        return intAt(data, 16) to intAt(data, 20)
    }
    if (data.size >= 4 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) {
        var i = 2
        while (i + 9 < data.size) {
            if (data[i] != 0xFF.toByte()) { i++; continue }
            while (i < data.size && data[i] == 0xFF.toByte()) i++
            if (i >= data.size) break
            val marker = data[i++].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7 || marker == 0x01) continue
            if (i + 1 >= data.size) break
            val segmentLength = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            if (segmentLength < 2 || i + segmentLength > data.size) break
            if (marker in setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)) {
                val height = ((data[i + 3].toInt() and 0xFF) shl 8) or (data[i + 4].toInt() and 0xFF)
                val width = ((data[i + 5].toInt() and 0xFF) shl 8) or (data[i + 6].toInt() and 0xFF)
                return width to height
            }
            i += segmentLength
        }
    }
    return 0 to 0
}

private fun intAt(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

private fun writeLeInt(out: OutputStream, value: Int) {
    out.write(value and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 24) and 0xFF)
}

private fun writeBeInt(out: OutputStream, value: Int) {
    out.write((value ushr 24) and 0xFF)
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
}

private const val VORBIS_VENDOR = "Fxxk-NCM"
