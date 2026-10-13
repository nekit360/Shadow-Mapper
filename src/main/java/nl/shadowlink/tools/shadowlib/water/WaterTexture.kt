package nl.shadowlink.tools.shadowlib.water

import nl.shadowlink.tools.shadowlib.texturedic.Texture
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic

/**
 * Which texture of a water dictionary is the surface.
 *
 * The install's `pc/textures/water.wtd` is a loose file (not in an `.img`) and holds a single texture,
 * `waterclear` - measured on both installs of this host, identical files (23 655 bytes, md5 `2472ba86…`).
 * The rule is therefore "the one that says water, else the first there is", so a modded or a different
 * version of the file still draws something instead of nothing.
 *
 * @return the texture, or null when the dictionary has none at all
 */
fun pickWaterSurface(textures: List<Texture>): Texture? =
    textures.firstOrNull { it.diffuseTexName.contains("water", ignoreCase = true) }
        ?: textures.firstOrNull()

/** [pickWaterSurface] for a parsed dictionary. */
fun pickWaterSurface(dictionary: TextureDic): Texture? = pickWaterSurface(dictionary.textures)
