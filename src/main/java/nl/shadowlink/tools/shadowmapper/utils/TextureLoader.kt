package nl.shadowlink.tools.shadowmapper.utils

import com.jogamp.opengl.GL
import com.jogamp.opengl.GL2
import nl.shadowlink.tools.shadowlib.model.model.TextureResolver
import nl.shadowlink.tools.shadowlib.texturedic.Texture
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic
import java.nio.ByteBuffer
import java.util.IdentityHashMap

/**
 * Uploads the dictionaries of the map to GL and hands the texture ids to the models.
 *
 * In GTA: IV a shader does not point at a texture but names it ("TextureSampler" holds the name of the
 * diffuse map, stored in the .wdr), and the same name can live in the dictionary embedded in the model
 * or in the TXD of its .ide entry - in NJ_01 the embedded dictionary alone answers only 43 % of the
 * names, the .ide TXD answers the rest. The renderer therefore asks each attached dictionary in turn.
 *
 * Only the textures a model really asks for are uploaded, and each one only once: uploading every
 * dictionary whole would put hundreds of megabytes of unused textures on the card.
 *
 * @author Shadow-Link
 */
class GlTextureCache(private val gl: GL2) : TextureResolver {

    /** the part of a dictionary that is needed at render time: names and the ids handed out so far */
    private inner class Dictionary(val dic: TextureDic) {
        private val texturesByName = HashMap<String, Texture>(dic.textures.size * 2)
        private val glIds = HashMap<String, Int>()

        init {
            dic.textures.forEach { texturesByName[it.diffuseTexName.lowercase()] = it }
        }

        /** @return the GL id, 0 when this dictionary has no texture with that name */
        fun resolve(name: String): Int {
            val key = name.lowercase()
            val cached = glIds[key]
            if (cached != null) {
                return cached
            }
            val texture = texturesByName[key] ?: return 0
            val id = texture.uploadToGl(gl)
            glIds[key] = id
            uploadedTextures++
            uploadedBytes += texture.data.size.toLong()
            return id
        }

        fun ids(): IntArray = glIds.values.toIntArray()
    }

    private val dictionaries = IdentityHashMap<TextureDic, Dictionary>()

    /** how many dictionaries were asked for something */
    var dictionariesUsed = 0
        private set

    /** how many textures were actually put on the card */
    var uploadedTextures = 0
        private set

    /** how many bytes of DXT data those textures are */
    var uploadedBytes = 0L
        private set

    /** how many shaders got their texture */
    var bound = 0
        private set

    /** how many shaders asked for a name no attached dictionary has */
    var missing = 0
        private set

    /** every distinct name a shader asked for in this load */
    private val requestedNames = HashSet<String>()

    /** the names that no attached dictionary could answer (for the log: the first few) */
    private val missingNames = LinkedHashSet<String>()

    val missingExamples: List<String>
        get() = missingNames.take(8).toList()

    val requestedCount: Int
        get() = requestedNames.size

    val missingNameCount: Int
        get() = missingNames.size

    override fun resolve(dictionary: TextureDic, textureName: String): Int {
        requestedNames.add(textureName.lowercase())
        val cache = dictionaries[dictionary] ?: Dictionary(dictionary).also {
            dictionaries[dictionary] = it
            dictionariesUsed++
        }
        val id = cache.resolve(textureName)
        if (id != 0) {
            bound++
        } else {
            missing++
            missingNames.add(textureName)
        }
        return id
    }

    /** Deletes every texture this cache put on the card (the map was released/reloaded). */
    fun dispose() {
        dictionaries.values.forEach { dictionary ->
            val ids = dictionary.ids()
            if (ids.isNotEmpty()) {
                gl.glDeleteTextures(ids.size, ids, 0)
            }
        }
        dictionaries.clear()
        requestedNames.clear()
        missingNames.clear()
        dictionariesUsed = 0
        uploadedTextures = 0
        uploadedBytes = 0
        bound = 0
        missing = 0
    }
}

/** Uploads a single texture and returns its GL id. */
fun Texture.uploadToGl(gl: GL2): Int {
    val textureId = IntArray(1)
    gl.glGenTextures(1, textureId, 0)
    toGlTexture(gl, textureId[0])
    return textureId[0]
}

fun TextureDic.toGl(gl: GL2): IntArray {
    val textureIds = Array(textures.size) { 0 }.toIntArray()
    gl.glGenTextures(textures.size, textureIds, 0)

    this.textures.forEachIndexed { index, texture -> texture.toGlTexture(gl, textureIds[index]) }
    return textureIds
}

private fun Texture.toGlTexture(gl: GL2, textureId: Int) {
    gl.glBindTexture(GL.GL_TEXTURE_2D, textureId)

    val compressionType = when (dxtCompressionType) {
        "DXT1" -> GL.GL_COMPRESSED_RGB_S3TC_DXT1_EXT
        "DXT3" -> GL.GL_COMPRESSED_RGBA_S3TC_DXT3_EXT
        "DXT5" -> GL.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
        else -> throw IllegalStateException("Unknown compression type")
    }

    gl.glCompressedTexImage2D(
        GL.GL_TEXTURE_2D, 0, compressionType, width, height, 0, data.size, ByteBuffer.wrap(data)
    )

    // gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, 3, width, height, 0,
    // gl.GL_RGBA, gl.GL_UNSIGNED_BYTE,
    // br.getByteBuffer(dataSize));

//        gl.glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
}
