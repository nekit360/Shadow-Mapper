package nl.shadowlink.tools.shadowlib.model.wdr

import nl.shadowlink.tools.io.ByteReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Where the vertex data of a geometry is.
 *
 * The vertex buffer structure carries two data words. The stock files write the same tagged pointer in
 * both, but a model exported by a tool (3ds Max -> GIMS -> OpenIV import) writes it in the second one and
 * leaves the first at 0 - and the reader took the first. A data offset of 0 means "graphics memory offset
 * 0", so **every** geometry of such a model read the vertices of the one geometry whose data happens to sit
 * there: 17 geometries of `electron_cinema_base.wdr` all drew the same cloud of vertices (a tangle of
 * stretched triangles), 193 of 193 geometries over the 81 `.wdr` of the modded install's `unost_square.img`
 * were affected, while the game draws those models correctly because it uses the tagged pointer.
 */
internal class VertexBufferTest {

    @Test
    fun `a buffer whose first data word is 0 takes the second one`() {
        val buffer = VertexBuffer(ByteReader(stream(firstData = 0, secondData = 0x50000080), 0))

        assertEquals(0x80, buffer.DataOffset)
        assertEquals(0x80, buffer.DataOffset2)
        assertEquals(1, buffer.VertexCount)
        assertEquals(4, buffer.RawData.size)
        assertEquals(DATA_BYTE, buffer.RawData[0])
    }

    @Test
    fun `a buffer that carries the pointer in both words keeps the first one`() {
        // the shape every stock .wdr has: measured over nj_02.img and manhat01.img, 6 255 geometries,
        // DataOffset == DataOffset2 in all of them
        val buffer = VertexBuffer(ByteReader(stream(firstData = 0x50000080, secondData = 0x50000080), 0))

        assertEquals(0x80, buffer.DataOffset)
        assertEquals(0x80, buffer.DataOffset2)
        assertEquals(DATA_BYTE, buffer.RawData[0])
    }

    @Test
    fun `a buffer without any data pointer keeps the zero it has`() {
        // bronx_e.img / bxe_el_windows01.wdr: both words are 0 when the data really is at graphics offset 0
        val buffer = VertexBuffer(ByteReader(stream(firstData = 0, secondData = 0), 0))

        assertEquals(0, buffer.DataOffset)
    }

    private val DATA_BYTE = 0x7E.toByte()

    /**
     * A drawable buffer and its surroundings: the structure at 0 with one vertex of four bytes, the vertex
     * declaration at `0x40` (16 bytes of it) and the vertex data at `0x80`.
     */
    private fun stream(firstData: Int, secondData: Int): ByteArray {
        val bytes = ByteArray(0x100)
        writeInt(bytes, 0x00, 0x6BBAD8)      // vtable
        writeShort(bytes, 0x04, 1)           // vertex count
        writeShort(bytes, 0x06, 0)           // bLocked, align
        writeInt(bytes, 0x08, firstData)
        writeInt(bytes, 0x0C, 4)             // stride
        writeInt(bytes, 0x10, 0x50000040)    // vertex declaration
        writeInt(bytes, 0x14, 0)
        writeInt(bytes, 0x18, secondData)
        writeInt(bytes, 0x1C, 0)
        writeInt(bytes, 0x40, 0)             // declaration: usage flags, stride, decoder, type, types
        bytes[0x80] = DATA_BYTE.toByte()
        return bytes
    }

    private fun writeShort(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xff).toByte()
        bytes[at + 1] = ((value shr 8) and 0xff).toByte()
    }

    private fun writeInt(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) bytes[at + i] = ((value shr (8 * i)) and 0xff).toByte()
    }
}
