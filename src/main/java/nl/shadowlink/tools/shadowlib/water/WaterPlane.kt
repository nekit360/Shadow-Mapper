package nl.shadowlink.tools.shadowlib.water

/**
 * One water plane: four corners of [WaterPoint.VALUES] values each, then `param` and `unknown`.
 *
 * The line is read by whitespace, not by a fixed separator, because the two shapes in the wild differ only in
 * how the two trailing values are spaced: the stock `water.dat` writes them behind the fourth corner
 * (`... 1.0 0.995   5 26.0`), a modded file writes them as a group of their own (`... 1.0   13 0.1`). Both
 * carry the same 30 values in the same order - `param` is the 29th, `unknown` the 30th. The old reader split
 * on three spaces and indexed `split[3].split(" ")[7]`, which threw on the second shape and on a blank line,
 * and took the whole map load with it (see `NEXT-STEPS.md` item 6).
 *
 * @author Shadow-Link
 */
class WaterPlane(line: String) {
    @JvmField
    var points = arrayOfNulls<WaterPoint?>(CORNERS)
    var param: Int
    var unknown: Float

    @JvmField
    var selected = false

    @JvmField
    var u = 0.0f

    @JvmField
    var v = 1.0f

    init {
        val values = line.trim().split(Regex("\\s+")).map { it.toFloat() }
        val needed = CORNERS * WaterPoint.VALUES + 2
        require(values.size >= needed) { "a water plane carries $needed values, this line has ${values.size}" }

        for (corner in 0 until CORNERS) {
            val from = corner * WaterPoint.VALUES
            points[corner] = WaterPoint(values.subList(from, from + WaterPoint.VALUES).toFloatArray())
        }
        param = values[CORNERS * WaterPoint.VALUES].toInt()
        unknown = values[CORNERS * WaterPoint.VALUES + 1]

        v = points[0]!!.coord.x - points[1]!!.coord.x
        if (Integer.signum(v.toInt()) == -1) v *= -1
        v /= 10
    }

    companion object {
        /** a water plane is a quad */
        const val CORNERS = 4
    }
}

/** the values of one corner as an array (the JVM has no bridge from a sublist to a FloatArray) */
private fun List<Float>.toFloatArray(): FloatArray {
    val out = FloatArray(size)
    for (i in indices) out[i] = this[i]
    return out
}
