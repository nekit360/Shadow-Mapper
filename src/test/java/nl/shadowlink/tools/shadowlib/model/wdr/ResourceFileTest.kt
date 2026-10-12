package nl.shadowlink.tools.shadowlib.model.wdr

import nl.shadowlink.tools.io.ByteReader
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.util.zip.Deflater

/**
 * The compressed payload of a resource is everything behind its header. A slice that stops short of
 * the end produces a stream that never finishes: the decompressed buffer stays full of zeros, the
 * first pointer read of the parser throws `IllegalStateException: Expected an offset` and the whole
 * dictionary (or model) is lost - 11 of 107 `.wtd` in `nj_01.img`, 32 of 101 in `nj_02.img`, all of
 * them small enough that the missing bytes were the end of the deflate stream.
 */
internal class ResourceFileTest {

    @Test
    fun `a small resource decompresses to its declared size`() {
        val content = ByteArray(4096) { (it % 251).toByte() }

        val stream = ResourceFile().read(ByteReader(resource(content), 0), -1)

        assertArrayEquals(content, stream.copyOf(content.size))
    }

    @Test
    fun `the payload is taken from the byte behind the header`() {
        // the first bytes of the payload are part of the comparison: a slice that started at the
        // wrong offset would decompress to something else (or fail with a format error)
        val content = ByteArray(4096) { if (it < 8) (0xA0 + it).toByte() else (it % 251).toByte() }

        val stream = ResourceFile().read(ByteReader(resource(content), 0), -1)

        assertArrayEquals(content, stream.copyOf(content.size))
    }

    /** a `.wtd`-like resource: the 14-byte header (system memory 4096, graphics 0, deflate) + payload */
    private fun resource(content: ByteArray): ByteArray {
        val header = ByteArray(14)
        writeInt(header, 0, 0x05435352)   // magic
        writeInt(header, 4, 8)            // type
        writeInt(header, 8, 0x00000010)   // flags: system = 16 shl 8 = 4096, graphics = 0
        header[12] = 0x78                 // codec 0xDA78 (deflate), little endian
        header[13] = 0xDA.toByte()
        return header + deflate(content)
    }

    private fun deflate(content: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(content)
        deflater.finish()
        val out = ByteArray(content.size * 2 + 64)
        val size = deflater.deflate(out)
        deflater.end()
        return out.copyOf(size)
    }

    private fun writeInt(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) bytes[at + i] = ((value shr (8 * i)) and 0xff).toByte()
    }
}
