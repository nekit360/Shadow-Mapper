package nl.shadowlink.tools.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * What `Vector4D.axisAngle` promises: the pair `glRotatef` takes to draw a placement the way **the game**
 * draws it.
 *
 * GTA: IV multiplies a placement quaternion the other way round (`v' = q* v q`), so the drawn rotation is
 * the one of the inverse quaternion and the axis of the pair comes out negated. The measurement behind
 * that statement is in `development/probes/lod_pair_convention.py`: a WPL record's `lod` field names the
 * instance index of the model's LOD copy, and an LOD is drawn *instead of* the model, so both occupy the
 * same volume - of the 4 537 pairs of the whole map the ones the two conventions disagree about decide
 * **38 to 3** for the inverse, with box centres that land within centimetres (`MHFWsign9_MH8` on its
 * gantry `HWAYLOD03_MH8`: 2 cm with the inverse, 15.8 m with the other one).
 *
 * The regression this file guards against is the sign of the axis: identity and 180-degree turns are
 * their own inverse and answer the same either way, so a map with the wrong sign looks "almost right"
 * and only models whose pivot is off their own centre slide to one side of where the game puts them.
 */
internal class Vector4DTest {

    @Test
    fun `a 90 degree yaw in the file is drawn as minus 90 about z, which is what the game does`() {
        val q = Vector4D(0f, 0f, sin(Math.PI / 4).toFloat(), cos(Math.PI / 4).toFloat())
        val axisAngle = q.axisAngle

        assertEquals(0f, axisAngle.x, 1e-5f)
        assertEquals(0f, axisAngle.y, 1e-5f)
        assertEquals(-1f, axisAngle.z, 1e-5f)   // the inverse quaternion's axis
        assertEquals(90f, axisAngle.w, 1e-3f)

        val turned = rotateByAxisAngle(1f, 0f, 0f, axisAngle)
        assertEquals(0f, turned[0], 1e-5f)
        assertEquals(-1f, turned[1], 1e-5f)     // x lands on -y; the other convention would say +y
        assertEquals(0f, turned[2], 1e-5f)
    }

    @Test
    fun `the axis-angle pair turns a vector the way the game draws the quaternion`() {
        val quaternions = listOf(
            yaw(Math.PI / 2),
            yaw(-Math.PI / 2),
            yaw(Math.PI / 4),
            yaw(-3 * Math.PI / 4),
            Vector4D(0.4f, 0.2f, 0.5f, 0.74162f),      // an arbitrary tilt, not a yaw (unit length)
            Vector4D(-0.5f, 0.5f, -0.5f, 0.5f),
        )
        for (q in quaternions) {
            val axisAngle = q.axisAngle
            // the game draws the inverse of the file's quaternion: q* v q
            val gameSide = Vector4D(-q.x, -q.y, -q.z, q.w)
            for (v in listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f))) {
                val byQuaternion = rotateByQuaternion(v, gameSide)
                val byAxisAngle = rotateByAxisAngle(v[0], v[1], v[2], axisAngle)
                for (i in 0..2) {
                    assertEquals(byQuaternion[i], byAxisAngle[i], 1e-4f,
                        "axisAngle of $q turns $v wrong")
                }
            }
        }
    }

    @Test
    fun `MHFWsign9_MH8 lands on the gantry the game draws it on`() {
        // manhat08_stream0.wpl places the model at this pivot with a 90-degree yaw, and its own box
        // centre is 7.9 m off that pivot - the pair the file names as its LOD (manhat08.wpl #433,
        // HWAYLOD03_MH8) stands at the sign's box centre. Measured from the game's own files.
        val placement = floatArrayOf(56.0519f, 1019.874f, 22.1214f)
        val q = Vector4D(0f, 0f, -0.7071068f, 0.7071068f)
        // manhat08.ide: MHFWsign9_MH8 bounds -29.7566, -1.86181, -8.53334 .. 13.9161, 1.85126, 1.94026
        val centre = floatArrayOf((-29.7566f + 13.9161f) / 2, (-1.86181f + 1.85126f) / 2, 0f)
        val gantry = floatArrayOf(56.037f, 1011.938f, 18.825f)   // HWAYLOD03_MH8 placement

        val turned = rotateByAxisAngle(centre[0], centre[1], centre[2], q.axisAngle)
        for (i in 0..1) {
            assertEquals(gantry[i], placement[i] + turned[i], 0.15f,
                "the sign's box centre must land on its gantry, axis " + q.axisAngle)
        }
    }

    @Test
    fun `a quaternion with no vector part is no rotation and still has an axis`() {
        for (w in listOf(1f, -1f)) {
            val axisAngle = Vector4D(0f, 0f, 0f, w).axisAngle
            // glRotatef with a zero axis is undefined - the pair must carry a valid one
            assertTrue(axisAngle.x * axisAngle.x + axisAngle.y * axisAngle.y + axisAngle.z * axisAngle.z > 0.9f)
            val turned = rotateByAxisAngle(1f, 0f, 0f, axisAngle)
            assertEquals(1f, turned[0], 1e-5f)   // identity: 0 degrees and 360 degrees both are
            assertEquals(0f, turned[1], 1e-5f)
            assertEquals(0f, turned[2], 1e-5f)
        }
    }

    @Test
    fun `a w the float error pushed past 1 does not make the angle NaN`() {
        val axisAngle = Vector4D(0.0001f, 0f, 0f, 1.0000001f).axisAngle
        assertTrue(axisAngle.w.isFinite(), "angle is " + axisAngle.w)
        assertEquals(0f, axisAngle.w, 1e-3f)
    }

    @Test
    fun `a quaternion a tool did not normalise answers the same rotation`() {
        val q = yaw(Math.PI / 2)
        val expected = q.axisAngle
        val actual = Vector4D(q.x * 0.8f, q.y * 0.8f, q.z * 0.8f, q.w * 0.8f).axisAngle
        assertEquals(expected.x, actual.x, 1e-5f)
        assertEquals(expected.y, actual.y, 1e-5f)
        assertEquals(expected.z, actual.z, 1e-5f)
        assertEquals(expected.w, actual.w, 1e-3f)
    }

    private fun yaw(radians: Double) =
        Vector4D(0f, 0f, sin(radians / 2).toFloat(), cos(radians / 2).toFloat())

    /** v' = v + qw * t + qv x t with t = 2 (qv x v) - the quaternion rotation */
    private fun rotateByQuaternion(v: FloatArray, q: Vector4D): FloatArray {
        val (x, y, z) = v
        val tx = 2f * (q.y * z - q.z * y)
        val ty = 2f * (q.z * x - q.x * z)
        val tz = 2f * (q.x * y - q.y * x)
        return floatArrayOf(
            x + q.w * tx + (q.y * tz - q.z * ty),
            y + q.w * ty + (q.z * tx - q.x * tz),
            z + q.w * tz + (q.x * ty - q.y * tx),
        )
    }

    /** what glRotatef(angle, axis) does to a vector */
    private fun rotateByAxisAngle(x: Float, y: Float, z: Float, rot: Vector4D): FloatArray {
        val radians = Math.toRadians(rot.w.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val t = 1f - c
        return floatArrayOf(
            (t * rot.x * rot.x + c) * x + (t * rot.x * rot.y - s * rot.z) * y + (t * rot.x * rot.z + s * rot.y) * z,
            (t * rot.x * rot.y + s * rot.z) * x + (t * rot.y * rot.y + c) * y + (t * rot.y * rot.z - s * rot.x) * z,
            (t * rot.x * rot.z - s * rot.y) * x + (t * rot.y * rot.z + s * rot.x) * y + (t * rot.z * rot.z + c) * z,
        )
    }
}
