package app.fxxkncm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class NcmDecoderTest {
    @Test
    fun rejectsInvalidHeader() {
        assertThrows(IllegalArgumentException::class.java) {
            NcmDecoder.decode(ByteArrayInputStream(ByteArray(16)), ByteArrayOutputStream())
        }
    }

    @Test
    fun rejectsTruncatedHeader() {
        assertThrows(EOFException::class.java) {
            NcmDecoder.decode(ByteArrayInputStream("CTEN".toByteArray(Charsets.US_ASCII)), ByteArrayOutputStream())
        }
    }

    @Test
    fun rejectsOversizedKeyLength() {
        val input = ByteArrayOutputStream()
        input.write("CTENFDAM".toByteArray(Charsets.US_ASCII))
        input.write(byteArrayOf(0, 0))
        val invalidLength = 1024 * 1024 + 1
        repeat(4) { shift -> input.write((invalidLength ushr (shift * 8)) and 0xff) }

        assertThrows(IllegalArgumentException::class.java) {
            NcmDecoder.decode(ByteArrayInputStream(input.toByteArray()), ByteArrayOutputStream())
        }
    }

    @Test
    fun rejectsKeyCiphertextWithInvalidBlockLength() {
        val input = ByteArrayOutputStream()
        input.write("CTENFDAM".toByteArray(Charsets.US_ASCII))
        input.write(byteArrayOf(0, 0))
        input.write(byteArrayOf(1, 0, 0, 0))
        input.write(0x64)

        assertThrows(IllegalArgumentException::class.java) {
            NcmDecoder.decode(ByteArrayInputStream(input.toByteArray()), ByteArrayOutputStream())
        }
    }

    @Test
    fun precomputedAudioMaskMatchesOriginalFormula() {
        val keyBox = ByteArray(256) { ((it * 73 + 19) and 0xff).toByte() }
        val mask = NcmDecoder.createAudioMask(keyBox)

        for (position in 0 until 256) {
            val boxValue = keyBox[position].toInt() and 0xff
            val maskIndex = (boxValue +
                (keyBox[(boxValue + position) and 0xff].toInt() and 0xff)) and 0xff
            assertEquals(keyBox[maskIndex], mask[position])
        }
    }
}
