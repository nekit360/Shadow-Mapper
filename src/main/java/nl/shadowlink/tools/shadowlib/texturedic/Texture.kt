package nl.shadowlink.tools.shadowlib.texturedic

/**
 *
 * @author Shadow-Link
 */
class Texture(
    val diffuseTexName: String,
    val dxtCompressionType: String?,
    val width: Int,
    val height: Int,
    val data: ByteArray,
) {
    @JvmField
    var compression = 0
    var offset = 0

    @JvmField
    var conversionOffset = 0

    @JvmField
    var dataSize = 0

    /**
     * GL name of this texture on the card, 0 while it is not uploaded.
     *
     * It lives here on purpose: the renderer must not keep a dictionary (and with it every DXT blob of
     * the dictionary) alive just to remember ids - the model owns its textures and drops them when it is
     * reset, which is what keeps a large load from running out of heap.
     */
    @JvmField
    var glTextureId = 0

    /** the alpha statistics of this texture, decoded on first use by [alpha] */
    private var alphaStats: AlphaStats? = null

    /**
     * What the alpha channel of this texture holds, decoded once from its DXT blocks and kept (see
     * [decodeAlphaStats]). The renderer asks this to decide whether a material has to be blended at all: a
     * diffuse map whose alpha is 1 everywhere looks the same whether it is blended or not, while one with a
     * real alpha channel disappears into the background when it is drawn opaque.
     *
     * @return the statistics, [AlphaStats.OPAQUE] for a texture without a decodable alpha channel
     */
    fun alpha(): AlphaStats {
        alphaStats?.let { return it }
        val stats = decodeAlphaStats()
        alphaStats = stats
        return stats
    }

    var alphaTexName: String? = null
}