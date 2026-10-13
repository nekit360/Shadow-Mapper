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
open class ItemObject(private val gameType: GameType) : IdeItem() {
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
     * The extra column an `anim` record carries between its texture and its draw distance - the name the
     * file gives the owner of the record (`nj_01`, `manhat09`, `public_2`, ... in each of the 160 `anim`
     * rows the clean install loads). Null for an `OBJS`/`TOBJ` record.
     */
    var animWad: String? = null

    /**
     * Set when this row did not have the standard column list and was read with one of the tolerance
     * rules of [IdeRecord] - today that means the `flags2` column was missing (see [read]), or the row
     * was an `anim` record whose extra column had to be dropped. `IDE` names such records in its log,
     * probes count them.
     */
    var relaxedRead = false
        protected set

    /**
     * Reads one `OBJS` row: `model, txd, drawDistance, flags1, flags2, bbMin(3), bbMax(3), sphere(4), wdd`.
     *
     * A row is not always that list. Measured on the modded install: `us_pavement_01, unosti_square,
     * 3000, 0, -85.3101, ...` - the column where `flags2` belongs holds the first bound instead,
     * because the tool that exported the file dropped one column. The signal is exactly the one this
     * reader used to die on: a column that is not an integer where the second flag is expected. Such a
     * row is read as the variant with **one** flags column, which puts every following column back where
     * its author put it - the row above comes out as the half extents of a 170x70 unit pavement
     * (`bbMin -85.3101 -34.9212 -0.0684118`, `bbMax 85.3101 34.9213 0.0684114`, sphere radius 92.1809).
     * Read strictly it threw `NumberFormatException` and took the whole map with it: 0 IPLs, 0 images
     * (`NEXT-STEPS.md` item 4).
     *
     * `flags2` becomes 0 in that case and [relaxedRead] is set. A row that ends early leaves the columns
     * it does not have at their defaults; a row that is not a row at all (no second column) still throws,
     * and `IDE` skips that one record with a warning instead of the file.
     *
     * The `anim` section is read here too - its rows are these rows with one column more (see [animWad]).
     */
    override fun read(line: String) {
        val record = IdeRecord(line)
        // An `anim` record is an `OBJS` record with one column more, right after the texture:
        // `LODNJ01Shop47, LODNJ01Block02, nj_01, 550, 1024, ...` - 17 columns against the 16 of an `OBJS`
        // row, uniform over all 310 `anim` rows of the install. The value where the draw distance belongs
        // is that extra name, so the column is dropped and the rest read as the `OBJS` row it is. Read
        // positionally the draw distance would come out as its default, every following column would sit
        // one place too far left and the `.wdd` name would be a number (`-4.76837E-7`) - the renderer
        // would look for a group of that name and draw nothing.
        val animRecord = record.size == ANIM_COLUMNS &&
                IdeRecord.float(record.fields[2]) == null && IdeRecord.float(record.fields[3]) != null
        val row = if (animRecord) record.without(2) else record
        animWad = if (animRecord) record.fields[2] else null
        val split = row.fields
        when (gameType) {
            GameType.GTA_IV -> {
                modelName = split[0]
                textureName = split[1]
                drawDistance = FloatArray(1)
                if (row.size > 4) {
                    drawDistance[0] = row.floatOr(2)
                    flag1 = row.intOr(3, 0)
                    // One flags column (the second was dropped, so everything behind it shifted left) or
                    // both of them - the bounds start one column further left in the first case.
                    val secondFlag = row.intOrNull(4)
                    val boundsAt = if (secondFlag == null) IdeRecord.BOUNDS_AFTER_ONE_FLAG
                    else IdeRecord.BOUNDS_AFTER_TWO_FLAGS
                    flag2 = secondFlag ?: 0
                    val bounds = row.bounds(boundsAt)
                    boundsMin = bounds.min
                    boundsMax = bounds.max
                    boundsSphere = bounds.sphere
                    WDD = bounds.wdd
                    relaxedRead = row.relaxed || secondFlag == null
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

    /**
     * The columns of this record as the file writes them, an `anim` record's extra column included when
     * the record came from that section. A subclass whose section carries columns of its own appends them
     * (see [ItemTimedObject]): writing an `anim` row without its column, or a `tobj` row without its time
     * window, would move the object into a section whose shape it does not have.
     */
    protected open fun rowLine(): String {
        var line = modelName
        line += ", $textureName"
        animWad?.let { line += ", $it" }
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
        return line
    }

    fun save(output: BufferedWriter) {
        try {
            output.write(rowLine())
            output.newLine()
        } catch (ex: IOException) {
            Logger.getLogger(ItemObject::class.java.name).log(Level.SEVERE, null, ex)
        }
    }

    companion object {
        /** Columns of an `anim` record: the 16 of an `OBJS` row plus its owner name after the texture. */
        const val ANIM_COLUMNS = 17
    }
}
