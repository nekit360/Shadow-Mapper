package nl.shadowlink.tools.shadowlib.ide

import nl.shadowlink.tools.shadowlib.utils.GameType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedWriter
import java.io.StringWriter
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

    @Test
    fun `an anim record is an objs record with the owner column in front of the draw distance`() {
        // pc/DATA/MAPS/JERSEY/NJ_01.IDE line 717, verbatim - 17 columns against the 16 of an OBJS row
        val item = readObject(
            "NJ01StripClub, NJ01Block03, nj_01, 90, 1024, 0, -26.6995, -11.0943, -6.8632, 26.6995, " +
                    "11.0845, 6.8632, -1.90735e-006, -0.00493526, -4.76837e-007, 29.7143, null"
        )

        assertEquals("NJ01StripClub", item.modelName)
        assertEquals("NJ01Block03", item.textureName)
        assertEquals("nj_01", item.animWad)
        assertEquals(90.0f, item.drawDistance[0], 0.0f)
        assertEquals(1024, item.flag1)
        assertEquals(0, item.flag2)
        assertEquals(-26.6995f, item.boundsMin.x, 0.0f)
        assertEquals(-11.0943f, item.boundsMin.y, 0.0f)
        assertEquals(6.8632f, item.boundsMax.z, 0.0f)
        assertEquals(29.7143f, item.boundsSphere.w, 0.0f)
        // Read positionally instead, the draw distance would default and the `.wdd` column would come
        // out as the row's last-but-two value, -4.76837E-7: the renderer would look for a group of that
        // name, find none, and draw nothing for the model.
        assertEquals("null", item.WDD)
        assertFalse(item.relaxedRead)

        val half = item.boundsMin
        assertEquals(sqrt(half.x * half.x + half.y * half.y + half.z * half.z), item.boundsSphere.w, 0.01f)
    }

    @Test
    fun `an anim record that names a wdd group keeps that name`() {
        // NJ_01.IDE line 709, verbatim - the group the renderer loads for this model
        val item = readObject(
            "LODNJ01Shop47, LODNJ01Block02, nj_01, 550, 1024, 0, -11.764, -16.976, -6.17379, 11.6544, " +
                    "16.8486, 5.89321, -0.0547972, -0.0637417, -0.140292, 21.4368, nj01_slod30_grp"
        )

        assertEquals("LODNJ01Shop47", item.modelName)
        assertEquals(550.0f, item.drawDistance[0], 0.0f)
        assertEquals("nj01_slod30_grp", item.WDD)
        assertFalse(item.relaxedRead)
    }

    @Test
    fun `a timed object reads the objs columns and its time window`() {
        // NJ_01.IDE line 649 (the `tobj` section), verbatim
        val item = readTimedObject(
            "NJ01Blck01Emiss, nj01_parentemiss, 100, 8192, 0, -76.6937, -16.9345, -8.5716, 76.6936, " +
                    "16.9346, 8.5716, -2.28882e-005, 1.90735e-005, 9.53674e-007, 79.0074, null, 31457343"
        )

        assertEquals("NJ01Blck01Emiss", item.modelName)
        assertEquals("nj01_parentemiss", item.textureName)
        assertEquals(100.0f, item.drawDistance[0], 0.0f)
        assertEquals(8192, item.flag1)
        // the `.wdd` name is the second-to-last column and the window is behind it, not the other way
        assertEquals("null", item.WDD)
        assertEquals(31457343, item.timedFlags)
        assertNull(item.animWad)
        assertFalse(item.relaxedRead)
    }

    @Test
    fun `a record is written back in the shape of its own section`() {
        // The columns are what matters here, not the text: a float goes back through `toString()`, so a
        // file's `-2.28882e-005` becomes `-2.28882E-5` - that is how this writer has always written and
        // it is not what this test is about.
        val anim = readObject(
            "LODNJ01Shop47, LODNJ01Block02, nj_01, 550, 1024, 0, -11.764, -16.976, -6.17379, 11.6544, " +
                    "16.8486, 5.89321, -0.0547972, -0.0637417, -0.140292, 21.4368, nj01_slod30_grp"
        ).let { saved(it).split(", ") }
        assertEquals(ItemObject.ANIM_COLUMNS, anim.size)
        assertEquals(
            listOf("LODNJ01Shop47", "LODNJ01Block02", "nj_01", "550.0", "1024", "0"), anim.take(6)
        )
        assertEquals("nj01_slod30_grp", anim[16])

        // a `tobj` row without its window would be a record the parser of that section cannot read
        val timed = readTimedObject(
            "NJ01Blck01Emiss, nj01_parentemiss, 100, 8192, 0, -76.6937, -16.9345, -8.5716, 76.6936, " +
                    "16.9346, 8.5716, -2.28882e-005, 1.90735e-005, 9.53674e-007, 79.0074, null, 31457343"
        ).let { saved(it).split(", ") }
        assertEquals(17, timed.size)
        assertEquals("NJ01Blck01Emiss", timed[0])
        assertEquals("null", timed[15])
        assertEquals("31457343", timed[16])
    }

    private fun saved(item: ItemObject): String {
        val out = StringWriter()
        val writer = BufferedWriter(out)
        item.save(writer)
        writer.flush()                          // save() writes through the buffer, the test wants the text
        return out.toString().trim()
    }

    private fun readTimedObject(line: String): ItemTimedObject {
        val item = ItemTimedObject(GameType.GTA_IV)
        item.read(line)
        return item
    }

    private fun readObject(line: String): ItemObject {
        val item = ItemObject(GameType.GTA_IV)
        item.read(line)
        return item
    }
}
