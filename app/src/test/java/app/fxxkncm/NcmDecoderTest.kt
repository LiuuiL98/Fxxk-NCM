package app.fxxkncm

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NcmDecoderTest {
    @Test
    fun rejectsInvalidHeader() {
        assertThrows(IllegalArgumentException::class.java) {
            NcmDecoder.decode(ByteArrayInputStream(ByteArray(16)), ByteArrayOutputStream())
        }
    }
}
