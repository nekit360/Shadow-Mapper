package nl.shadowlink.tools.shadowlib.model.model

import nl.shadowlink.tools.io.ByteReader
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic
import nl.shadowlink.tools.shadowlib.utils.GameType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The name -> GL texture id part of the model, without a GL context: Element asks a resolver for every
 * shader's texture name and stores the answer in the shader (Shader.glTex), which is what the renderer
 * later binds per strip.
 */
internal class ElementTexturesTest {

    /**
     * An empty dictionary: a TXD that holds no texture can be built without a GL context, and for this
     * test only the identity of the dictionary matters (the resolver is the stub).
     */
    private fun dictionary(name: String = "test.txd"): TextureDic =
        TextureDic(name, ByteReader(ByteArray(0), 0), GameType.GTA_SA, false, 0)

    @Test
    fun `when the resolver knows the name, then the shader gets its id`() {
        val element = Element()
        element.createShader("nj01_kerb4")
        element.attachTXD(dictionary())

        element.bindTextures { _, name -> if (name == "nj01_kerb4") 42 else 0 }

        assertEquals(42, element.getShader(0).getGLTex())
    }

    @Test
    fun `when no dictionary has the name, then the shader gets 0 and not a stale id`() {
        val element = Element()
        element.createShader("nj01_kerb4")
        element.attachTXD(dictionary())

        element.bindTextures { _, _ -> 7 }              // a first binding that found something
        assertEquals(7, element.getShader(0).getGLTex())

        element.bindTextures { _, _ -> 0 }              // a later load without that texture
        assertEquals(0, element.getShader(0).getGLTex())
    }

    @Test
    fun `when the first dictionary does not have the name, then the second one is asked`() {
        val element = Element()
        element.createShader("nj01_kerb4")
        element.attachTXD(dictionary("embedded.txd"))
        element.attachTXD(dictionary("nj01_27.wtd"))

        val asked = mutableListOf<String>()
        element.bindTextures { txd, _ ->
            asked.add(txd?.fileName ?: "anywhere")
            if (txd?.fileName == "nj01_27.wtd") 99 else 0
        }

        assertEquals(listOf("embedded.txd", "nj01_27.wtd"), asked)
        assertEquals(99, element.getShader(0).getGLTex())
    }

    @Test
    fun `when no attached dictionary has the name, then the resolver is asked without one`() {
        val element = Element()
        element.createShader("nj01_kerb4")
        element.attachTXD(dictionary("embedded.txd"))

        val asked = mutableListOf<TextureDic?>()
        element.bindTextures { txd, _ ->
            asked.add(txd)
            0                                              // nothing finds it, the pool included
        }

        assertEquals(2, asked.size)
        assertEquals(null, asked[1], "the last attempt must be the one without a dictionary")
        assertEquals(0, element.getShader(0).getGLTex())
    }

    @Test
    fun `when a shader has no texture name, then the resolver is not asked for it`() {
        val element = Element()
        element.createShader(null)
        element.attachTXD(dictionary())

        var asked = 0
        element.bindTextures { _, _ ->
            asked++
            5
        }

        assertEquals(0, asked)
        assertEquals(0, element.getShader(0).getGLTex())
        assertEquals("", element.getShader(0).getTextureName())
    }

    @Test
    fun `when a model has no dictionary, then the name still gets its chance`() {
        val element = Element()
        element.createShader("nj01_kerb4")

        val asked = mutableListOf<TextureDic?>()
        element.bindTextures { txd, _ ->
            asked.add(txd)
            5
        }

        assertEquals(listOf<TextureDic?>(null), asked)
        assertEquals(5, element.getShader(0).getGLTex())
    }
}
