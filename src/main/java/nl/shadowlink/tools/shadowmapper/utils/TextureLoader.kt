package nl.shadowlink.tools.shadowmapper.utils

import com.jogamp.opengl.GL
import com.jogamp.opengl.GL2
import nl.shadowlink.tools.shadowlib.model.model.TextureResolver
import nl.shadowlink.tools.shadowlib.texturedic.Texture
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic
import java.nio.ByteBuffer

/**
 * Uploads the textures of the map to GL and hands the texture ids to the models.
 *
 * In GTA: IV a shader does not point at a texture but names it ("TextureSampler" holds the name of the
 * diffuse map, stored in the .wdr), and the same name can live in the dictionary embedded in the model
 * or in the TXD of its .ide entry - in NJ_01 the embedded dictionary alone answers only 43 % of the
 * names, the .ide TXD answers the rest. The renderer therefore asks each attached dictionary in turn.
 *
 * Only the textures a model really asks for are uploaded, each one only once (the id is stored on the
 * [Texture] itself), and the cache holds nothing but the ids it created: the memory of a dictionary is
 * released together with the model that brought it in. Holding the dictionaries here instead made a load
 * of the whole map (all 481 IPLs) die with `OutOfMemoryError` after a few gigabytes.
 *
 * @author Shadow-Link
 */
class GlTextureCache(private val gl: GL2) : TextureResolver {

    /** the GL names this cache created, so a reload can delete exactly them (ints, nothing else) */
    private val createdIds = ArrayList<Int>()

    /** the dictionaries that were asked for a name, by file name - for the log, and it holds no data */
    private val dictionaryNames = HashSet<String>()

    /**
     * Called when a name is in none of the dictionaries the model brought in.
     *
     * The renderer looks for a further dictionary of the image it is working on here and returns it (or
     * null when that image has no such texture); the texture is then taken from the returned dictionary.
     * GTA: IV resolves a texture name against what is streamed, not against one model's TXD, so a model
     * regularly asks for a name that lives in the dictionary of a neighbouring model.
     */
    var onNameMissing: ((String) -> TextureDic?)? = null

    /** how many shaders a dictionary of the current image could satisfy (not the model's own ones) */
    var fromImage = 0
        private set

    /** how many dictionaries were asked for something */
    val dictionariesUsed: Int
        get() = dictionaryNames.size

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

    override fun resolve(dictionary: TextureDic?, textureName: String): Int {
        requestedNames.add(textureName.lowercase())
        // no dictionary: the last resort - the dictionaries this model brought in do not have the name,
        // so it may live in another dictionary of the image being loaded
        var holder = dictionary
        if (holder == null) {
            holder = onNameMissing?.invoke(textureName)
        }
        val texture = holder?.findTexture(textureName)
        if (texture == null) {
            // only the last resort counts as "not found": a single attached dictionary that does not have
            // the name is a partial answer, the next one may still have it
            if (dictionary == null) {
                missing++
                missingNames.add(textureName)
            }
            return 0
        }
        if (texture.glTextureId == 0) {
            texture.glTextureId = texture.uploadToGl(gl)
            createdIds.add(texture.glTextureId)
            uploadedTextures++
            uploadedBytes += texture.data.size.toLong()
        }
        bound++
        holder?.let { dictionaryNames.add(it.fileName.lowercase()) }
        if (dictionary == null) {
            fromImage++
        }
        return texture.glTextureId
    }

    /** Deletes every texture this cache put on the card (the map was released/reloaded). */
    fun dispose() {
        if (createdIds.isNotEmpty()) {
            val ids = createdIds.toIntArray()
            gl.glDeleteTextures(ids.size, ids, 0)
            createdIds.clear()
        }
        dictionaryNames.clear()
        requestedNames.clear()
        missingNames.clear()
        uploadedTextures = 0
        uploadedBytes = 0
        bound = 0
        missing = 0
        fromImage = 0
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
