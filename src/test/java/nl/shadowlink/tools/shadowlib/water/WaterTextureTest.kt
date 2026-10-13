package nl.shadowlink.tools.shadowlib.water

import nl.shadowlink.tools.shadowlib.texturedic.Texture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Which texture of a water dictionary gets drawn.
 *
 * The install's `pc/textures/water.wtd` holds one texture, `waterclear` (both installs of this host carry the
 * same 23 655-byte file, md5 `2472ba86…`), so the interesting cases are the ones a different version could
 * bring: several textures, none whose name says water, and an empty dictionary.
 */
internal class WaterTextureTest {

    @Test
    fun `a texture whose name says water wins over the first of the list`() {
        val first = texture("sand")
        val water = texture("waterclear")

        assertEquals(water, pickWaterSurface(listOf(first, water)))
    }

    @Test
    fun `a dictionary without such a name still draws its first texture`() {
        val first = texture("details")
        val second = texture("noise")

        assertEquals(first, pickWaterSurface(listOf(first, second)))
    }

    @Test
    fun `an empty dictionary picks nothing`() {
        assertNull(pickWaterSurface(emptyList()))
    }

    private fun texture(name: String) = Texture(name, "DXT5", 256, 256, ByteArray(0))
}
