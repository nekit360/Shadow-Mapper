package nl.shadowlink.tools.shadowlib.ide

import nl.shadowlink.tools.io.Vector3D
import nl.shadowlink.tools.io.Vector4D
import nl.shadowlink.tools.shadowlib.utils.GameType
import java.io.BufferedWriter
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author Shadow-Link
 */
class ItemTimedObject(private val gameType: GameType) : IdeItem() {
    lateinit var modelName: String
    var textureName: String? = null
    var objectCount = 0
    var drawDistance: FloatArray = floatArrayOf()
    var flag1 = 0
    var flag2 = 0
    var boundsMin = Vector3D(0.0f, 0.0f, 0.0f)
    var boundsMax = Vector3D(0.0f, 0.0f, 0.0f)
    var boundsSphere = Vector4D(0.0f, 0.0f, 0.0f, 0.0f)
    var WDD: String? = null
    var timedFlags = 0

    /** Set when the row was read with a tolerance rule of [IdeRecord] (see [ItemObject.read]). */
    var relaxedRead = false
        private set

    /**
     * Reads one `TOBJ` row - the same columns as `OBJS` plus the timed flags at the end:
     * `model, txd, drawDistance, flags1, flags2, bbMin(3), bbMax(3), sphere(4), wdd, timedFlags`.
     * The tolerance rules are those of [ItemObject.read]: a column that is not an integer where a flag
     * is expected means the file dropped a flags column, and every following column sits one further
     * left - including the two behind the bounds.
     */
    override fun read(line: String) {
        val record = IdeRecord(line)
        val split = record.fields
        modelName = split[0]
        textureName = split[1]
        drawDistance = FloatArray(1)
        drawDistance[0] = record.floatOr(2)
        flag1 = record.intOr(3, 0)
        val secondFlag = record.intOrNull(4)
        val boundsAt = if (secondFlag == null) IdeRecord.BOUNDS_AFTER_ONE_FLAG
        else IdeRecord.BOUNDS_AFTER_TWO_FLAGS
        flag2 = secondFlag ?: 0
        val bounds = record.bounds(boundsAt)
        boundsMin = bounds.min
        boundsMax = bounds.max
        boundsSphere = bounds.sphere
        WDD = bounds.wdd
        timedFlags = record.intOr(boundsAt + 11, 0)
        relaxedRead = record.relaxed || secondFlag == null
    }

    fun save(output: BufferedWriter) {
        try {
            var line = modelName
            line += ", $textureName"
            line += ", " + drawDistance[0]
            line += ", $flag1"
            line += ", $flag2"
            line += ", " + boundsMin.x
            line += ", " + boundsMin.y
            line += ", " + boundsMin.z
            line += ", " + boundsMax.x
            line += ", " + boundsMax.y
            line += ", " + boundsMax.z
            line += ", " + boundsSphere.x
            line += ", " + boundsSphere.y
            line += ", " + boundsSphere.z
            line += ", " + boundsSphere.w
            line += ", $WDD"
            line += ", $timedFlags"
            output.write(
                """
    $line
    
    """.trimIndent()
            )
            println("Line: $line")
        } catch (ex: IOException) {
            Logger.getLogger(ItemObject::class.java.name).log(Level.SEVERE, null, ex)
        }
    }
}