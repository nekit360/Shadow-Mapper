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
class ItemObject(private val gameType: GameType) : IdeItem() {
    var id = 0

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

    /**
     * Set when this row did not have the standard column list and was read with one of the tolerance
     * rules of [IdeRecord] - today that means the `flags2` column was missing (see [read]). `IDE`
     * names such records in its log, probes count them.
     */
    var relaxedRead = false
        private set

    /**
     * Reads one `OBJS` row: `model, txd, drawDistance, flags1, flags2, bbMin(3), bbMax(3), sphere(4), wdd`.
     *
     * A row is not always that list. Measured on the modded install: `us_pavement_01, unosti_square,
     * 3000, 0, -85.3101, ...` - the column where `flags2` belongs holds the first bound instead,
     * because the tool that exported the file dropped one column. The signal is exactly the one this
     * reader used to die on: a column that is not an integer where the second flag is expected. Such a
     * row is read as the variant with **one** flags column, which puts every following column back
     * where its author put it - the row above comes out as the half extents of a 170x70 unit pavement
     * (`bbMin -85.3101 -34.9212 -0.0684118`, `bbMax 85.3101 34.9213 0.0684114`, sphere radius
     * 92.1809). Read strictly it threw `NumberFormatException` and took the whole map with it: 0 IPLs,
     * 0 images (`NEXT-STEPS.md` item 4).
     *
     * `flags2` becomes 0 in that case and [relaxedRead] is set. A row that ends early leaves the
     * columns it does not have at their defaults; a row that is not a row at all (no second column)
     * still throws, and `IDE` skips that one record with a warning instead of the file.
     */
    override fun read(line: String) {
        val record = IdeRecord(line)
        val split = record.fields
        when (gameType) {
            GameType.GTA_IV -> {
                modelName = split[0]
                textureName = split[1]
                drawDistance = FloatArray(1)
                if (record.size > 4) {
                    drawDistance[0] = record.floatOr(2)
                    flag1 = record.intOr(3, 0)
                    // One flags column (the second was dropped, so everything behind it shifted left)
                    // or both of them - the bounds start one column further left in the first case.
                    val secondFlag = record.intOrNull(4)
                    val boundsAt = if (secondFlag == null) IdeRecord.BOUNDS_AFTER_ONE_FLAG
                    else IdeRecord.BOUNDS_AFTER_TWO_FLAGS
                    flag2 = secondFlag ?: 0
                    val bounds = record.bounds(boundsAt)
                    boundsMin = bounds.min
                    boundsMax = bounds.max
                    boundsSphere = bounds.sphere
                    WDD = bounds.wdd
                    relaxedRead = record.relaxed || secondFlag == null
                }
            }

            GameType.GTA_SA -> {
                id = Integer.valueOf(split[0])
                modelName = split[1]
                textureName = split[2]
                drawDistance = FloatArray(1)
                drawDistance[0] = java.lang.Float.valueOf(split[3])
                flag1 = Integer.valueOf(split[4])
            }

            else -> {
                id = Integer.valueOf(split[0])
                modelName = split[1]
                textureName = split[2]
                objectCount = Integer.valueOf(split[3])
                drawDistance = FloatArray(objectCount)
                var i = 0
                while (i < objectCount) {
                    drawDistance[i] = java.lang.Float.valueOf(split[4 + i])
                    i++
                }
                flag1 = Integer.valueOf(split[4 + objectCount])
            }
        }
        display()
    }

    fun display() {
        // if (gameType != Constants.gIV) {
        // //Message.displayMsgHigh("ID: " + id);
        // }
        // //Message.displayMsgHigh("ModelName: " + modelName);
        // //Message.displayMsgHigh("TextureName: " + textureName);
        // if (gameType == Constants.gIII || gameType == Constants.gVC) {
        // //Message.displayMsgHigh("ObjectCount: " + objectCount);
        // for (int i = 0; i < objectCount; i++) {
        // //Message.displayMsgHigh("DrawDistance" + i + ": " + drawDistance[i]);
        // }
        // }
        // if (gameType == Constants.gIV) {
        // //Message.displayMsgHigh("Flag: " + flag1);
        // //Message.displayMsgHigh("Flag2: " + flag2);
        // //Message.displayMsgHigh("BoundsMAX: " + boundsMax.x + ", " + boundsMax.y + ", " + boundsMax.z);
        // //Message.displayMsgHigh("BoundsMIN: " + boundsMin.x + ", " + boundsMin.y + ", " + boundsMin.z);
        // //Message.displayMsgHigh("BoundsSphere: " + boundsSphere.x + ", " + boundsSphere.y + ", " + boundsSphere.z +
        // ", " + boundsSphere.w);
        // //Message.displayMsgHigh("WDD: " + WDD);
        // }
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
            output.write(line)
            output.newLine()
            println("Line: $line")
        } catch (ex: IOException) {
            Logger.getLogger(ItemObject::class.java.name).log(Level.SEVERE, null, ex)
        }
    }
}