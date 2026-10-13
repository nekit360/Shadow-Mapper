package nl.shadowlink.tools.shadowlib.ide

import nl.shadowlink.tools.shadowlib.utils.GameType

/**
 * One record of an `.ide`'s `tobj` section - an object the game draws inside a time window.
 *
 * The columns are those of an `OBJS` row plus the time window at the end, 17 of them, uniform over the
 * 2 752 `tobj` rows of the install:
 *
 * ```
 * NJ01Blck01Emiss, nj01_parentemiss, 100, 8192, 0, -76.6937, -16.9345, ... 79.0074, null, 31457343
 * ```
 *
 * [ItemObject.read] reads the object part (tolerance rules included), and the time window is the column
 * behind the `.wdd` name - which sits one place further left when the file dropped the second flags
 * column, hence the same rule as in [ItemObject.read].
 *
 * A WPL places these objects as ordinary instances (the district's night windows and emissive LODs:
 * `NJ01_Swo5_emissLOD`, `bridgeglow_bkn`); the IDE declares them here rather than in `objs`, and the IDE
 * lookup used to search `objs` only - 1 555 instances of the clean install drew nothing because of it.
 */
class ItemTimedObject(gameType: GameType) : ItemObject(gameType) {

    /** The time window this object is drawn in, the last column of a `tobj` row. */
    var timedFlags = 0

    override fun read(line: String) {
        super.read(line)
        val record = IdeRecord(line)
        val boundsAt = if (record.intOrNull(4) == null) IdeRecord.BOUNDS_AFTER_ONE_FLAG
        else IdeRecord.BOUNDS_AFTER_TWO_FLAGS
        timedFlags = record.intOr(boundsAt + 11, 0)
    }

    override fun rowLine(): String = super.rowLine() + ", $timedFlags"
}
