package nl.shadowlink.tools.shadowlib.ide

import nl.shadowlink.tools.shadowlib.utils.GameType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * An `OBJS` row is read positionally, and the files in the wild are not always the list the reader
 * expects. The measured case is the modded install: `pc/data/maps/first_district/unost_square.ide`
 * line 29 carries one `flags` column instead of two, and the column where the second flag belongs
 * holds the first bound. The strict reader threw `NumberFormatException` there, and because [IDE]
 * called it straight from the record loop, that one row ended the whole map load: 46 of 114 `.ide`
 * files read, 0 IPLs, 0 images.
 */
internal class ItemObjectTest {

    @Test
    fun `the standard row is read column for column`() {
        // electron_cinema_sign of the modded install's unost_square.ide
        val item = readObject(
            "electron_cinema_sign, electron_cinema, 100, 12582912, 0, -2.59912, -11.1983, -1.70338, " +
                    "2.59912, 11.1983, 1.70338, 0.0, 0.0, 0.0, 11.6215, null"
        )

        assertEquals("electron_cinema_sign", item.modelName)
        assertEquals("electron_cinema", item.textureName)
        assertEquals(100.0f, item.drawDistance[0], 0.0f)
        assertEquals(12582912, item.flag1)
        assertEquals(0, item.flag2)
        assertEquals(-2.59912f, item.boundsMin.x, 0.0f)
        assertEquals(-1.70338f, item.boundsMin.z, 0.0f)
        assertEquals(1.70338f, item.boundsMax.z, 0.0f)
        assertEquals(11.1983f, item.boundsMax.y, 0.0f)
        assertEquals(11.6215f, item.boundsSphere.w, 0.0f)
        assertEquals("null", item.WDD)
        assertFalse(item.relaxedRead)
    }

    @Test
    fun `a row without the second flags column puts the bounds back where its author put them`() {
        // unost_square.ide line 29 of the modded install, verbatim
        val item = readObject(
            "us_pavement_01, unosti_square, 3000, 0, -85.3101, -34.9212, -0.0684118, 85.3101, 34.9213, " +
                    "0.0684114, -1.52588e-05, 3.05176e-05, -2.38419e-07, 92.1809, null"
        )

        assertEquals("us_pavement_01", item.modelName)
        assertEquals("unosti_square", item.textureName)
        assertEquals(3000.0f, item.drawDistance[0], 0.0f)
        assertEquals(0, item.flag1)
        assertEquals(0, item.flag2)
        assertEquals(-85.3101f, item.boundsMin.x, 0.0f)
        assertEquals(-34.9212f, item.boundsMin.y, 0.0f)
        assertEquals(-0.0684118f, item.boundsMin.z, 0.0f)
        assertEquals(85.3101f, item.boundsMax.x, 0.0f)
        assertEquals(34.9213f, item.boundsMax.y, 0.0f)
        assertEquals(0.0684114f, item.boundsMax.z, 0.0f)
        assertEquals(92.1809f, item.boundsSphere.w, 0.0f)
        assertEquals("null", item.WDD)
        assertTrue(item.relaxedRead)

        // Not decoration: the radius the row carries is the diagonal of the half extents it carries,
        // so the columns land exactly where the author put them. A reader that defaulted the missing
        // column instead of shifting would put a bound into `flag2` and shift every following value.
        val half = item.boundsMin
        assertEquals(sqrt(half.x * half.x + half.y * half.y + half.z * half.z), item.boundsSphere.w, 0.01f)
    }

    @Test
    fun `a row that ends early leaves the columns it does not have at their defaults`() {
        val item = readObject("us_pavement_01, unosti_square, 3000, 0, -85.3101, -34.9212")

        assertEquals(3000.0f, item.drawDistance[0], 0.0f)
        assertEquals(-85.3101f, item.boundsMin.x, 0.0f)
        assertEquals(-34.9212f, item.boundsMin.y, 0.0f)
        assertEquals(0.0f, item.boundsMin.z, 0.0f)
        assertEquals(0.0f, item.boundsMax.x, 0.0f)
        assertEquals(0.0f, item.boundsSphere.w, 0.0f)
        assertNull(item.WDD)
        assertTrue(item.relaxedRead)
    }

    @Test
    fun `a run of letters where a number belongs is reported and does not throw`() {
        val item = readObject(
            "us_pavement_01, unosti_square, three thousand, 0, 0, -85.3101, -34.9212, -0.0684118, 85.3101, " +
                    "34.9213, 0.0684114, -1.52588e-05, 3.05176e-05, -2.38419e-07, 92.1809, null"
        )

        assertEquals(0.0f, item.drawDistance[0], 0.0f)
        assertEquals(-85.3101f, item.boundsMin.x, 0.0f)
        assertTrue(item.relaxedRead)
    }

    @Test
    fun `the stock row without bounds keeps the reading it has always had`() {
        // common/data/default.ide of both installs: three rows with no bounds and one flags column,
        // and this reader has always left everything behind the dictionary name empty for them
        val item = readObject("airtrain_vlo, generic, 2000, 0")

        assertEquals("airtrain_vlo", item.modelName)
        assertEquals("generic", item.textureName)
        assertEquals(0.0f, item.drawDistance[0], 0.0f)
        assertEquals(0, item.flag1)
        assertEquals(0, item.flag2)
        assertEquals(0.0f, item.boundsSphere.w, 0.0f)
        assertNull(item.WDD)
        assertFalse(item.relaxedRead)
    }

    private fun readObject(line: String): ItemObject {
        val item = ItemObject(GameType.GTA_IV)
        item.read(line)
        return item
    }
}
