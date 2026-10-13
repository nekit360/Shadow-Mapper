package nl.shadowlink.tools.shadowlib.water

import nl.shadowlink.tools.io.Vector3D

/**
 * One corner of a water plane: `x y z speedX speedY unknown waveHeight`.
 *
 * @author Shadow-Link
 */
class WaterPoint(values: FloatArray) {
    @JvmField
    var coord: Vector3D
    var speedX: Float
    var speedY: Float
    var unknown: Float
    var waveHeight: Float

    init {
        coord = Vector3D(values[0], values[1], values[2])
        speedX = values[3]
        speedY = values[4]
        unknown = values[5]
        waveHeight = values[6]
    }

    companion object {
        /** how many values one corner carries */
        const val VALUES = 7
    }
}
