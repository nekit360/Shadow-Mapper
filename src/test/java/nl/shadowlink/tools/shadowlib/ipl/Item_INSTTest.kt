package nl.shadowlink.tools.shadowlib.ipl

import nl.shadowlink.tools.io.Vector4D
import nl.shadowlink.tools.shadowlib.utils.GameType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The instance's placement rotation has to reach the pair the renderer actually draws (`axisAngle`).
 *
 * The binary reader of GTA: IV only filled `rotation`, so `axisAngle` stayed `(0, 0, 0, 0)` and
 * `glRotatef(0, 0, 0, 0)` draws no rotation at all: every instance whose quaternion is not identity was
 * drawn in its raw model orientation - buildings standing diagonally across their sidewalk, which is
 * what "some models are misplaced" looked like.
 *
 * The *direction* of the drawn rotation is `Vector4D.axisAngle`'s business (and its tests): the game
 * applies the inverse quaternion, so the axis of the pair comes out negated.
 */
internal class Item_INSTTest {

    @Test
    fun `setting the rotation refreshes the axis angle the renderer draws`() {
        val item = Item_INST(GameType.GTA_IV)
        item.rotation = Vector4D(0f, 0f, sin(Math.PI / 4).toFloat(), cos(Math.PI / 4).toFloat())

        assertEquals(-1f, item.axisAngle.z, 1e-5f)   // the inverse quaternion's axis, see Vector4DTest
        assertEquals(90f, item.axisAngle.w, 1e-3f)
    }

    @Test
    fun `a text placement line ends up with the same axis angle`() {
        val item = Item_INST(GameType.GTA_SA)
        item.read("7, model, 0, 100.0, 200.0, 5.0, 0.0, 0.0, 0.70710677, 0.70710677, -1")

        assertEquals(-1f, item.axisAngle.z, 1e-5f)
        assertEquals(90f, item.axisAngle.w, 1e-3f)
    }

    @Test
    fun `an instance that was never read draws no rotation either`() {
        val item = Item_INST(GameType.GTA_IV)
        val turned = item.axisAngle
        assertEquals(0f, turned.w, 1e-5f) // identity, not a zero axis
        assertEquals(1f, turned.z, 1e-5f)
    }
}
