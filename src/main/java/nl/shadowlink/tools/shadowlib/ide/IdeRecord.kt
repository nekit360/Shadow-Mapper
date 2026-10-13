package nl.shadowlink.tools.shadowlib.ide

import nl.shadowlink.tools.io.Vector3D
import nl.shadowlink.tools.io.Vector4D

/**
 * The columns of one `.ide` record, read the tolerant way.
 *
 * A record is a fixed list of columns, but the files in the wild are not always the list the reader
 * expects. Measured on the modded install (`pc/data/maps/first_district/unost_square.ide`):
 * `us_pavement_01, unosti_square, 3000, 0, -85.3101, ...` - the column where the second `flags` value
 * belongs holds the first bound instead, because the tool that exported the file dropped one column.
 * Indexing the columns and calling `Integer.valueOf` on whatever sat at the index threw
 * `NumberFormatException`, and since the readers are called from the file loop of [IDE], that one row
 * took the whole load with it: the map came up with 0 IPLs and 0 images.
 *
 * The rule here: a column that cannot be read as its kind becomes the default and is *reported*
 * ([relaxed]) instead of throwing, and a record that ends early leaves the remaining fields at their
 * defaults. A structural absurdity (a record with no second column at all) still throws, so that
 * [IDE] can skip exactly that record with a warning naming the file and the line.
 */
internal class IdeRecord(line: String) {
    /** The columns, spaces stripped (the readers have always stripped them) and empty tails dropped. */
    val fields: List<String> = line.replace(" ", "").split(",").dropLastWhile { it.isEmpty() }

    val size: Int get() = fields.size

    /** Set when a column could not be read as written: it held something else, or it was not there. */
    var relaxed = false
        private set

    /** The column as an integer, or null when it is not one (a fractional value, a name, absent). */
    fun intOrNull(index: Int): Int? = int(fields.getOrNull(index))

    /** The column as an integer, or [default] when it is not one; either way [relaxed] is set. */
    fun intOr(index: Int, default: Int): Int {
        val value = int(fields.getOrNull(index))
        if (value == null) {
            relaxed = true
            return default
        }
        return value
    }

    /** The column as a float, or [default] when it is not one (the bounds of a truncated record). */
    fun floatOr(index: Int, default: Float = 0.0f): Float {
        val value = float(fields.getOrNull(index))
        if (value == null) {
            relaxed = true
            return default
        }
        return value
    }

    /** The column as text; null when the record has no such column (an optional trailing name). */
    fun textOrNull(index: Int): String? = fields.getOrNull(index)

    /**
     * The same columns without the one at [index]. An `anim` record carries one column more than an
     * `OBJS` row (see [ItemObject.animWad]), and dropping that column turns the record into the row it
     * is a variant of, so the rest can be read with the rules above.
     */
    fun without(index: Int): IdeRecord =
        IdeRecord(fields.filterIndexed { i, _ -> i != index }.joinToString(","))

    /**
     * The ten bound floats and the `.wdd` name that follow the flags. [at] is the column of
     * `boundsMin.x`: 5 for a record with both flags, 4 for one with a single flags column, where
     * every following column has shifted one to the left.
     */
    fun bounds(at: Int): IdeBounds = IdeBounds(
        Vector3D(floatOr(at), floatOr(at + 1), floatOr(at + 2)),
        Vector3D(floatOr(at + 3), floatOr(at + 4), floatOr(at + 5)),
        Vector4D(floatOr(at + 6), floatOr(at + 7), floatOr(at + 8), floatOr(at + 9)),
        textOrNull(at + 10)
    )

    companion object {
        /** Column of `boundsMin.x` when a record has both flags columns (the standard record). */
        const val BOUNDS_AFTER_TWO_FLAGS = 5

        /** Column of `boundsMin.x` when a record carries only one flags column. */
        const val BOUNDS_AFTER_ONE_FLAG = 4

        /** The value as an integer, or null when it is absent, fractional or not numeric at all. */
        fun int(value: String?): Int? = if (value.isNullOrEmpty()) null else value.toIntOrNull()

        /** The value as a float, or null when it is absent or not numeric. */
        fun float(value: String?): Float? = if (value.isNullOrEmpty()) null else value.toFloatOrNull()
    }
}

/** The tail of an `OBJS`/`TOBJ` record: the bounds of the object and the name of its `.wdd` group. */
internal class IdeBounds(
    val min: Vector3D,
    val max: Vector3D,
    val sphere: Vector4D,
    val wdd: String?
)
