package nl.shadowlink.tools.shadowlib.ide

import nl.shadowlink.tools.shadowlib.utils.GameType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * One unreadable record must not take the load down. Measured on the modded install: a single `.ide`
 * row one column short threw out of `ItemObject.read`, through the constructor of [IDE], into the
 * loader thread - the map came up with the 46 `.ide` files `default.dat` names and nothing else:
 * 0 IPLs, 0 images, no district, no textures.
 */
internal class IDETest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `a record no reader can read is skipped and named, the file is read to its end`() {
        val loaded = read(
            """
            objs
            good_one, tex_a, 100, 12582912, 0, -1.0, -2.0, -3.0, 1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 3.7, null
            just_a_name
            good_two, tex_b, 200, 12582912, 0, -1.0, -2.0, -3.0, 1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 7.4, null
            end
            """.trimIndent()
        )

        assertEquals(listOf("good_one", "good_two"), loaded.itemObjs.map { it.modelName })
        assertEquals(2, loaded.readRecords)
        assertEquals(1, loaded.skippedRecords)
    }

    @Test
    fun `a row without the second flags column is read, not skipped`() {
        val loaded = read(
            """
            objs
            us_pavement_01, unosti_square, 3000, 0, -85.3101, -34.9212, -0.0684118, 85.3101, 34.9213, 0.0684114, -1.52588e-05, 3.05176e-05, -2.38419e-07, 92.1809, null
            end
            """.trimIndent()
        )

        assertEquals(1, loaded.itemObjs.size)
        assertEquals(1, loaded.readRecords)
        assertEquals(0, loaded.skippedRecords)
        assertTrue(loaded.itemObjs[0].relaxedRead)
        assertEquals(-85.3101f, loaded.itemObjs[0].boundsMin.x, 0.0f)
    }

    private fun read(content: String): IDE {
        val file = dir.resolve("test.ide").toFile()
        file.writeText(content)
        return IDE(file.absolutePath, GameType.GTA_IV, true)
    }
}
