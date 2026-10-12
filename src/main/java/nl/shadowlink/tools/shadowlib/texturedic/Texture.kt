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

    var alphaTexName: String? = null
}