package nl.shadowlink.tools.shadowmapper.render

import com.jogamp.opengl.GL
import com.jogamp.opengl.GL2
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic
import nl.shadowlink.tools.shadowlib.water.pickWaterSurface
import nl.shadowlink.tools.shadowmapper.FileManager
import nl.shadowlink.tools.shadowmapper.gui.PickingType
import nl.shadowlink.tools.shadowmapper.utils.uploadToGl

/**
 * The water planes of `water.dat`, drawn with the water texture of the install.
 *
 * The texture is the loose `pc/textures/water.wtd` (the game keeps it outside the images): measured, both
 * installs of this host carry the same file, 23 655 bytes, one texture `waterclear`, 256x256 DXT5.
 *
 * The texture is read on the **first frame that has water planes**, not from the GL init: the GL init runs
 * before an install is chosen, so `gameDir` is still null there and the read fails on `nullpc\textures\water.wtd`.
 * Loading is repeated whenever the install changes, and everything stays optional - without a texture the
 * planes are still drawn as a translucent blue surface, which is what the "Water" checkbox in the scene panel
 * promises. Until this was wired up, `waterTex` stayed 0 and not a single plane was drawn.
 *
 * The dictionary is held as the only strong reference to the uploaded texture, and it is one file with one
 * texture - unlike the per-model dictionaries the map load has to be careful with.
 *
 * @author Shadow-Link
 */
class RenderWater(
    private val fm: FileManager
) {
    /** the dictionary the surface texture came from, so its GL name can be deleted again */
    private var waterDic: TextureDic? = null

    /** GL name of the water surface texture, 0 while there is none on the card */
    private var waterTex = 0

    /** the install the texture was read from, so another one replaces it */
    private var loadedFor: String? = null

    /** GL name of the water surface texture, for probes (0 = the planes are drawn untextured) */
    val textureId: Int
        get() = waterTex

    fun display(gl: GL2) {
        if (fm.waters.isEmpty()) return
        ensureTexture(gl)
        drawWater(gl)
    }

    /** Deletes the uploaded texture (the install was closed). */
    fun dispose(gl: GL2) {
        if (waterTex != 0) {
            gl.glDeleteTextures(1, intArrayOf(waterTex), 0)
            waterTex = 0
        }
        waterDic = null
        loadedFor = null
    }

    /**
     * Reads and uploads the water texture of the loaded install, once per install. Does nothing while no
     * install is chosen or while its `water.wtd` is missing - the next frame tries again, so the planes are
     * still drawn with a flat colour in the meantime.
     */
    private fun ensureTexture(gl: GL2) {
        val dir = fm.installDir ?: return
        if (dir == loadedFor) return
        if (waterTex != 0) dispose(gl)
        loadedFor = dir
        try {
            val dic = fm.loadWaterTexture()
            val texture = pickWaterSurface(dic)
            if (texture == null) {
                println("Water: ${dic.fileName} holds no texture, the planes stay a flat surface")
                return
            }
            waterTex = texture.uploadToGl(gl)
            waterDic = dic
            println(
                "Water: texture ${texture.diffuseTexName} (${texture.width}x${texture.height}, " +
                        "${texture.dxtCompressionType}, ${texture.data.size} bytes) from ${dic.fileName}"
            )
        } catch (ex: Exception) {
            println("Water: no texture (${ex.javaClass.simpleName}: ${ex.message}), the planes stay a flat surface")
        }
    }

    companion object {
        /**
         * The tint the textured surface is drawn with. The game colours its water with a shader - the reflection
         * and refraction of what surrounds it, plus the type the plane's `param` selects - and it draws this
         * grey ripple texture through that; neither is reproduced here, so the texture is tinted instead of
         * drawn as the grey fog it is on its own.
         */
        private val WATER_TINT = floatArrayOf(0.55f, 0.75f, 1.0f, 0.8f)
    }

    private fun drawWater(gl: GL2) {
        gl.glPushName(PickingType.water)
        if (waterTex != 0) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, waterTex)
        }
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA)
        gl.glEnable(GL.GL_BLEND)
        gl.glBegin(GL2.GL_QUADS)
        fm.waters[0].planes.forEachIndexed { index, plane ->
            // a selected plane is tinted red so it can be told apart from the rest
            when {
                plane.selected -> gl.glColor4f(1.0f, 0.4f, 0.4f, 0.9f)
                waterTex != 0 -> gl.glColor4f(WATER_TINT[0], WATER_TINT[1], WATER_TINT[2], WATER_TINT[3])
                else -> gl.glColor4f(0.0f, 0.4f, 1.0f, 0.5f)
            }
            val p0 = plane.points[0]!!.coord
            val p1 = plane.points[1]!!.coord
            val p2 = plane.points[2]!!.coord
            val p3 = plane.points[3]!!.coord
            gl.glPushName(index)
            gl.glTexCoord2f(plane.u, plane.u)
            gl.glVertex3f(p0.x, p0.y, p0.z)
            gl.glTexCoord2f(plane.u, plane.v)
            gl.glVertex3f(p2.x, p2.y, p2.z)
            gl.glTexCoord2f(plane.v, plane.v)
            gl.glVertex3f(p3.x, p3.y, p3.z)
            gl.glTexCoord2f(plane.v, plane.u)
            gl.glVertex3f(p1.x, p1.y, p1.z)
            gl.glPopName()
        }
        gl.glEnd()
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0)
        gl.glPopName()
    }
}
