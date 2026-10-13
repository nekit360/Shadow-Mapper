package nl.shadowlink.tools.shadowlib.ide

import nl.shadowlink.tools.shadowlib.utils.GameType

/**
 * One record of an `.ide`'s `anim` section.
 *
 * Measured over the whole install: an `anim` record is an `OBJS` record with **one column more**, between
 * the texture and the draw distance - the owner name the file gives the record (`nj_01`, `manhat09`,
 * `public_2`, ...):
 *
 * ```
 * LODNJ01Shop47, LODNJ01Block02, nj_01, 550, 1024, 0, -11.764, -16.976, ... 21.4368, nj01_slod30_grp
 * ```
 *
 * 17 columns against the 16 of an `OBJS` row, uniform over all 310 `anim` rows of the install (160 of
 * them in the ides a clean 1.2.0.59 loads, in 27 files - `manhat09.ide` 55, `nj_01.ide` 22, ...).
 * [ItemObject.read] knows that shape, so this class adds nothing to the object: it exists to give the
 * section its own list ([IDE.itemAnim]) and its own place in the file on save.
 *
 * Before this, the renderer saw none of these records: [IDE] read them into a list it never stored, so
 * every instance of an `anim` model came out as `I really can't find in IDE` and drew nothing at all -
 * a tree, a club, a garage or a whole LOD block missing from the map (the blue gaps of `STATUS.md`,
 * "Models the editor could not find in the IDE").
 */
class ItemAnimated(gameType: GameType) : ItemObject(gameType)
