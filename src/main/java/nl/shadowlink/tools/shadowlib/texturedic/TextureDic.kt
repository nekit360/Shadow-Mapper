package nl.shadowlink.tools.shadowlib.texturedic

import nl.shadowlink.tools.io.ByteReader
import nl.shadowlink.tools.shadowlib.utils.GameType

/**
 * @author Shadow-Link
 */
class TextureDic(
    var fileName: String,
    var br: ByteReader,
    private val gameType: GameType,
    private val compressed: Boolean,
    sysSize: Int
) {

    var textureCount = 0
    var flags = 0
    var size = 0

    @JvmField
    var textures = ArrayList<Texture>()
    var fileSize = sysSize

    init {
        loadTextureDic()
    }

    private fun loadTextureDic(): Boolean {
        when (gameType) {
            GameType.GTA_IV -> if (compressed) {
                TextureDicIV().loadTextureDic(this, compressed, 0)
            } else {
                TextureDicIV().loadTextureDic(this, compressed, fileSize)
            }

            else -> TextureDic_III_ERA().loadTextureDic(this)
        }
        return true
    }

    fun addTexture(tex: Texture) {
        textures.add(tex)
    }

    /** name (lower case) -> texture, built on first use; dies with the dictionary */
    private var texturesByName: HashMap<String, Texture>? = null

    /**
     * Looks up a texture by the name a shader asks for. GTA: IV matches texture names case-insensitively
     * (the models use CamelCase, the dictionaries lower case).
     *
     * @return the texture, or null when this dictionary does not have that name
     */
    fun findTexture(name: String): Texture? {
        val key = name.lowercase()
        val index = texturesByName ?: HashMap<String, Texture>(textures.size * 2).also { index ->
            textures.forEach { index[it.diffuseTexName.lowercase()] = it }
            texturesByName = index
        }
        return index[key]
    }
}