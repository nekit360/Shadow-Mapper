package nl.shadowlink.tools.shadowlib.water

import nl.shadowlink.tools.shadowlib.utils.GameType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * `water.dat` lines, and the two shapes one line comes in.
 *
 * Every plane carries the same 30 values: four corners of seven (`x y z speedX speedY unknown waveHeight`) and
 * then `param` and `unknown`. The stock file writes those two behind the fourth corner
 * (`... 0.995   5 26.0`), a modded file writes them as a group of their own (`... 0.995   5 26.0` with three
 * spaces before them). The old reader split on three spaces and indexed `split[3].split(" ")[7]`, so the second
 * shape and a plain blank line both threw - and this is the **last** step of the map load, so the exception left
 * the editor's loading dialog open at 96 % (the modded install's file has 13 of the one shape, 1 of the other
 * and 6 blank lines).
 */
internal class WaterTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `both shapes give the same plane`() {
        val behindTheCorner = read(
            "-3000.0 -3000.0 3.0001 0.0 0.0 1.0 26.0   -3000.0 3000.0 3.0001 0.0 0.0 1.0 26.0   " +
                    "3000.0 -3000.0 3.0001 0.0 0.0 1.0 26.0   3000.0 3000.0 3.0001 0.0 0.0 1.0 0.995   5 26.0"
        )
        val asOwnGroup = read(
            "-3000.0 -3000.0 3.0001 0.0 0.0 1.0 26.0   -3000.0 3000.0 3.0001 0.0 0.0 1.0 26.0   " +
                    "3000.0 -3000.0 3.0001 0.0 0.0 1.0 26.0   3000.0 3000.0 3.0001 0.0 0.0 1.0 0.995   13 0.1"
        )

        assertEquals(1, behindTheCorner.planes.size)
        assertEquals(1, asOwnGroup.planes.size)

        val a = behindTheCorner.planes[0]
        val b = asOwnGroup.planes[0]
        for (corner in 0 until 4) {
            assertEquals(a.points[corner]!!.coord.x, b.points[corner]!!.coord.x, 0.0f)
            assertEquals(a.points[corner]!!.coord.y, b.points[corner]!!.coord.y, 0.0f)
            assertEquals(a.points[corner]!!.coord.z, b.points[corner]!!.coord.z, 0.0f)
        }
        assertEquals(5, a.param)
        assertEquals(26.0f, a.unknown, 0.0f)
        assertEquals(13, b.param)
        assertEquals(0.1f, b.unknown, 0.0f)
        assertEquals(26.0f, a.points[0]!!.waveHeight, 0.0f)
        assertEquals(0.995f, a.points[3]!!.waveHeight, 0.0f)
    }

    @Test
    fun `the corners keep the order of the line`() {
        val water = read("1 2 3 4 5 6 7   11 12 13 14 15 16 17   21 22 23 24 25 26 27   31 32 33 34 35 36 37   5 26.0")

        val plane = water.planes[0]
        assertEquals(1.0f, plane.points[0]!!.coord.x, 0.0f)
        assertEquals(11.0f, plane.points[1]!!.coord.x, 0.0f)
        assertEquals(21.0f, plane.points[2]!!.coord.x, 0.0f)
        assertEquals(31.0f, plane.points[3]!!.coord.x, 0.0f)
        assertEquals(4.0f, plane.points[0]!!.speedX, 0.0f)
        assertEquals(5.0f, plane.points[0]!!.speedY, 0.0f)
        assertEquals(6.0f, plane.points[0]!!.unknown, 0.0f)
        assertEquals(7.0f, plane.points[0]!!.waveHeight, 0.0f)
        assertEquals(5, plane.param)
        assertEquals(26.0f, plane.unknown, 0.0f)
    }

    @Test
    fun `blank lines and comments are skipped, a line no plane can be built from is reported`() {
        val water = read(
            """
            # a comment
            1 2 3 4 5 6 7   11 12 13 14 15 16 17   21 22 23 24 25 26 27   31 32 33 34 35 36 37   5 26.0

            1 2 3
            not a plane at all
            1 2 3 4 5 6 7   11 12 13 14 15 16 17   21 22 23 24 25 26 27   31 32 33 34 35 36 37   13 0.1
            """.trimIndent()
        )

        assertEquals(2, water.planes.size)
        assertEquals(2, water.skippedLines)   // the comment and the empty line
        assertEquals(2, water.failedLines)    // the three values and the line of words
    }

    private fun read(content: String): Water {
        val file = dir.resolve("water.dat").toFile()
        file.writeText(content)
        return Water(file.absolutePath, GameType.GTA_IV)
    }
}
