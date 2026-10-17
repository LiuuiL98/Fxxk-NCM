package app.fxxkncm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class AudioMetadataWriterTest {
    @Test
    fun flacWriterAddsVorbisCommentsAndCoverAndPreservesAudio() {
        val raw = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x66, 0x4C, 0x61, 0x43)) // fLaC
            write(byteArrayOf(0x00, 0x00, 0x00, 0x22)) // STREAMINFO, not final
            write(ByteArray(34) { it.toByte() })
            write(byteArrayOf(0x81.toByte(), 0x00, 0x00, 0x03)) // final padding block
            write(byteArrayOf(0, 0, 0))
            write(byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0xAA.toByte(), 0xBB.toByte()))
        }.toByteArray()
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val tags = NcmAudioTags("着迷 (Fascination)", listOf("迷心"), "都市金阁寺", image, "image/jpeg")
        val output = ByteArrayOutputStream()
        val writer = FlacMetadataOutputStream(output, tags)
        writer.write(raw)
        writer.finish()
        val result = output.toByteArray()

        assertTrue(result.copyOfRange(0, 4).contentEquals(byteArrayOf(0x66, 0x4C, 0x61, 0x43)))
        val blocks = mutableListOf<Int>()
        var pos = 4
        var lastType = -1
        while (pos + 4 <= result.size) {
            val first = result[pos].toInt() and 0xFF
            val type = first and 0x7F
            val last = first and 0x80 != 0
            val size = ((result[pos + 1].toInt() and 0xFF) shl 16) or
                ((result[pos + 2].toInt() and 0xFF) shl 8) or (result[pos + 3].toInt() and 0xFF)
            if (size < 0 || pos + 4 + size > result.size) break
            blocks += type
            pos += 4 + size
            if (last) {
                lastType = type
                break
            }
        }
        assertEquals(listOf(0, 1, 4, 6), blocks)
        assertEquals(6, lastType)
        val rest = result.copyOfRange(pos, result.size)
        assertTrue(rest.contentEquals(byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0xAA.toByte(), 0xBB.toByte())))
        val content = result.toString(Charsets.UTF_8)
        assertTrue(content.contains("TITLE=着迷 (Fascination)"))
        assertTrue(content.contains("ARTIST=迷心"))
        assertTrue(content.contains("ALBUM=都市金阁寺"))
        assertFalse(content.contains("TITLE=old"))
    }

    @Test
    fun mp3WriterAddsId3v23TextFramesAndArtwork() {
        val tags = NcmAudioTags("标题", listOf("歌手"), "专辑",
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()), "image/jpeg")
        val output = ByteArrayOutputStream()
        val writer = Mp3MetadataOutputStream(output, tags)
        val audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64)
        writer.write(audio)
        writer.finish()
        val result = output.toByteArray()

        assertTrue(result.copyOfRange(0, 3).contentEquals(byteArrayOf(0x49, 0x44, 0x33)))
        assertEquals(3, result[3].toInt())
        val contents = result.toString(Charsets.ISO_8859_1)
        assertTrue(contents.contains("TIT2"))
        assertTrue(contents.contains("TPE1"))
        assertTrue(contents.contains("TALB"))
        assertTrue(contents.contains("APIC"))
        assertTrue(result.takeLast(audio.size).toByteArray().contentEquals(audio))
    }
}
