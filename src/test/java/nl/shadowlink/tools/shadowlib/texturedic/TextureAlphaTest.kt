package nl.shadowlink.tools.shadowlib.texturedic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The alpha channel of a diffuse map is what decides whether a material belongs in the renderer's transparent
 * pass, so the decoder behind `Texture.alpha()` is checked here with blocks whose texels are known by hand: a
 * mask that keeps every texel, one that drops every texel, and one that drops half of them - the last is the
 * case the threshold exists for. No GL and no game data is needed for any of it.
 */
internal class TextureAlphaTest {

    /** a DXT5 texture of one block (4x4) whose 16 texels carry the given 3-bit alpha indices */
    private fun dxt5(indices: IntArray, a0: Int = 255, a1: Int = 0): Texture {
        val data = ByteArray(16)
        data[0] = a0.toByte()
        data[1] = a1.toByte()
        indices.forEachIndexed { i, value ->
            val bit = 3 * i
            val at = 2 + bit / 8
            data[at] = (data[at].toInt() or ((value shl (bit % 8)) and 0xff)).toByte()
            if (bit % 8 > 5) {
                data[at + 1] = (data[at + 1].toInt() or (value shr (8 - bit % 8))).toByte()
            }
        }
        // the colour part of the block, never read for the alpha of a DXT5 texture
        return Texture("hand", "DXT5", 4, 4, data)
    }

    @Test
    fun `a dxt5 texture whose alpha keeps every texel is opaque`() {
        val stats = dxt5(IntArray(16) { 0 }).alpha()

        assertEquals(16, stats.sampled)
        assertEquals(1f, stats.mean)
        assertEquals(0f, stats.belowHalf)
        assertFalse(stats.carriesTransparency)
    }

    @Test
    fun `a dxt5 texture whose alpha drops half of its texels is transparent`() {
        // index 1 is the second endpoint, which is 0 here: half of the texels vanish
        val stats = dxt5(IntArray(16) { if (it % 2 == 0) 0 else 1 }).alpha()

        assertEquals(0.5f, stats.mean)
        assertEquals(0.5f, stats.belowHalf)
        assertTrue(stats.carriesTransparency, "a texture that loses half of itself has to be blended")
    }

    @Test
    fun `a dxt5 texture that drops every texel is transparent`() {
        val stats = dxt5(IntArray(16) { 1 }).alpha()

        assertEquals(0f, stats.mean)
        assertEquals(1f, stats.belowHalf)
        assertTrue(stats.carriesTransparency)
    }

    @Test
    fun `a dxt3 texture expands its four-bit alpha`() {
        val data = ByteArray(16)
        // the low nibble of a byte is the first texel: four texels keep everything, twelve keep nothing
        data[0] = 0xff.toByte()
        data[1] = 0xff.toByte()
        val stats = Texture("hand", "DXT3", 4, 4, data).alpha()

        assertEquals(0.25f, stats.mean, 0.01f)
        assertEquals(0.75f, stats.belowHalf, 0.01f)
        assertTrue(stats.carriesTransparency)
    }

    @Test
    fun `a dxt1 texture is opaque in its four-colour mode`() {
        val data = ByteArray(8)
        data[0] = 0xff.toByte()     // color0 = 0x00ff, color1 = 0x0000: color0 > color1, so all texels exist
        data[1] = 0x00.toByte()
        val stats = Texture("hand", "DXT1", 4, 4, data).alpha()

        assertEquals(1f, stats.mean)
        assertFalse(stats.carriesTransparency, "the fourth index of that mode is a colour, not a hole")
    }

    @Test
    fun `a dxt1 texture in its one-bit-alpha mode drops the texels of index three`() {
        val data = ByteArray(8)
        data[0] = 0x00.toByte()     // color0 = 0 < color1 = 0x00ff: the mode with a transparent index
        data[1] = 0x00.toByte()
        data[2] = 0xff.toByte()
        data[3] = 0x00.toByte()
        // four texels (index 3) transparent, twelve opaque: 25 % is above the threshold
        data[4] = 0xc0.toByte()     // texels 0-3 = 0b11
        data[5] = 0x3f.toByte()
        val stats = Texture("hand", "DXT1", 4, 4, data).alpha()

        assertEquals(0.25f, stats.belowHalf)
        assertTrue(stats.carriesTransparency)
    }

    @Test
    fun `a texture of an unknown format is answered as opaque`() {
        val stats = Texture("hand", "ATI2N", 4, 4, ByteArray(16)).alpha()
        assertEquals(AlphaStats.OPAQUE, stats)
        assertEquals(1f, stats.mean)
        assertFalse(stats.carriesTransparency)
    }

    @Test
    fun `a truncated block does not invent texels`() {
        val data = ByteArray(8)     // a DXT5 block is 16 bytes
        val stats = Texture("hand", "DXT5", 4, 4, data).alpha()

        assertEquals(AlphaStats.OPAQUE, stats, "half a block is no measurement")
    }

    @Test
    fun `the statistics are decoded once and kept`() {
        val texture = dxt5(IntArray(16) { 1 })

        val first = texture.alpha()
        val second = texture.alpha()
        assertTrue(first === second, "the second call has to answer the value decoded by the first")
    }
}
