package nl.shadowlink.tools.io

import kotlin.math.sqrt

/**
 * @author Shadow-Link
 */
class Vector4D(
    @JvmField
    var x: Float = 0.0f,
    @JvmField
    var y: Float = 0.0f,
    @JvmField
    var z: Float = 0.0f,
    @JvmField
    var w: Float = 0.0f
) {

    operator fun plus(v4d: Vector4D) {
        x += v4d.x
        y += v4d.y
        z += v4d.z
        w += v4d.w
    }

    operator fun minus(v4d: Vector4D) {
        x -= v4d.x
        y -= v4d.y
        z -= v4d.z
        w -= v4d.w
    }

    operator fun plus(v3d: Vector3D) {
        x += v3d.x
        y += v3d.y
        z += v3d.z
    }

    operator fun minus(v3d: Vector3D) {
        x -= v3d.x
        y -= v3d.y
        z -= v3d.z
    }

    fun print(name: String) {
        println("$name: $x, $y, $z, $w")
    }

    /**
     * This quaternion as the axis-angle pair `glRotatef` takes to draw the placement **the way the game
     * draws it**: the axis is the file's axis *negated*, the angle is `2 * acos(w)` in degrees.
     *
     * GTA: IV applies a placement quaternion with the opposite multiplication order - it turns a
     * model-space vector `v` into `q* v q`, not into `q v q*` - so the rotation that lands on the map is
     * the one of the **inverse** quaternion, and `glRotatef` has to be given that one.
     *
     * This is not a matter of taste, it is measured against the game's own data: a WPL record's `lod`
     * field names the instance index of the model's simplified LOD copy, and the LOD is a stand-in drawn
     * *instead of* the model, so the two occupy the same volume. Of the 4 537 LOD pairs of the whole map
     * (4 496 are ties - identity and 180-degree turns are their own inverse), the pairs where the two
     * conventions disagree decide **38 to 3** for the inverse, with the LOD's box centre within 30 cm of
     * the model's (`MHFWsign9_MH8` on its gantry `HWAYLOD03_MH8`: 2 cm with the inverse, 15.8 m with the
     * other one). `development/probes/lod_pair_convention.py` is that measurement.
     *
     * A model whose pivot is not the centre of its own box is where this shows: `MHFWsign9_MH8` has its
     * centre 7.9 m off its pivot, so the wrong sign of the axis slides it 15.8 m to one side of its
     * gantry ("the MHFWsign9_MH8 model looks moved to the left"). Identity and 180-degree turns answer
     * both ways the same, which is why a map looks "almost right".
     *
     * The axis is negated and not the angle, so the pair keeps the file's turn as a positive angle; the
     * two are the same rotation. `glRotatef` normalises the axis.
     */
    val axisAngle: Vector4D
        get() {
            val rot = Vector4D()
            val len = sqrt(x * x + y * y + z * z)
            val norm = sqrt(x * x + y * y + z * z + w * w)
            if (len < 1e-6f || norm < 1e-6f) {
                // no vector part: (0, 0, 0, 1) is no rotation and (0, 0, 0, -1) is 360 degrees, which is
                // one as well - give glRotatef a valid axis (its angle is 0 either way)
                rot.z = 1.0f
                return rot
            }
            // the axis of the inverse quaternion: the same length, the opposite direction
            rot.x = -x / len
            rot.y = -y / len
            rot.z = -z / len
            // the angle is read off the *normalised* quaternion (the file's are unit, but a float error
            // or a tool's export need not be), and clamped: acos past 1 is NaN, and a NaN angle draws
            // nothing at all
            val clampedW = (w / norm).coerceIn(-1.0f, 1.0f)
            rot.w = (Math.acos(clampedW.toDouble()) * 2.0 * (180.0 / Math.PI)).toFloat() // radians to degrees
            return rot
        }

    override fun toString(): String {
        return "$x, $y, $z, $w"
    }

    fun toVector3D(): Vector3D {
        return Vector3D(x, y, z)
    }
}