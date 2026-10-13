package nl.shadowlink.tools.shadowlib.model.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The material preset a .wdr shader carries decides how the renderer has to draw it, and the only part of
 * that decision a unit test can check without a GL context is which preset means "cut this material up".
 *
 * Measured over the map (`ShaderSpsProbe`, `probes/README.md`): the presets of the foliage and the cut-outs
 * are `gta_trees.sps` and `gta_cutout.sps`; `gta_alpha.sps` and the `*_alpha` presets are the *blended*
 * materials (glass, decals) and must keep blending, which is why they must not answer true here.
 */
internal class ShaderPresetTest {

    private fun cutout(sps: String?): Boolean {
        val shader = Shader()
        shader.shaderSps = sps
        return shader.isCutout
    }

    @Test
    fun `the foliage and cut-out presets are cut-outs`() {
        assertTrue(cutout("gta_trees.sps"))
        assertTrue(cutout("gta_cutout.sps"))
        assertTrue(cutout("gta_foliage.sps"))
        assertTrue(cutout("GTA_TREES.SPS"), "the preset is compared without case")
    }

    @Test
    fun `the blended and the opaque presets are not cut-outs`() {
        assertFalse(cutout("gta_alpha.sps"))
        assertFalse(cutout("gta_normal_alpha.sps"))
        assertFalse(cutout("gta_spec_alpha.sps"))
        assertFalse(cutout("gta_emissive_alpha.sps"))
        assertFalse(cutout("gta_normal_spec.sps"))
        assertFalse(cutout("gta_default.sps"))
        assertFalse(cutout("gta_decal.sps"))
    }

    @Test
    fun `a shader without a preset is not a cut-out`() {
        assertFalse(cutout(""))
        assertFalse(cutout(null), "a model of a format without a preset (a .dff) must keep the old path")
    }

    @Test
    fun `the element keeps the preset of the wdr shader`() {
        val element = Element()
        element.createShader(listOf("ag_elm_leaf_summer_alpha"), "shader 0x1234", "gta_trees.sps")

        val shader = element.getShader(0)
        assertEquals("gta_trees.sps", shader.shaderSps)
        assertTrue(shader.isCutout)
        assertEquals(listOf("ag_elm_leaf_summer_alpha"), shader.textureNames)
    }

    @Test
    fun `a shader created without a preset stays opaque`() {
        val element = Element()
        element.createShader("nj01_kerb4")

        assertEquals("", element.getShader(0).shaderSps)
        assertFalse(element.getShader(0).isCutout)
    }

    private fun glass(sps: String?): Boolean {
        val shader = Shader()
        shader.shaderSps = sps
        return shader.isGlass
    }

    @Test
    fun `the glass presets are recognised`() {
        assertTrue(glass("gta_glass_spec.sps"))
        assertTrue(glass("gta_glass_reflect.sps"))
        assertTrue(glass("gta_glass_normal_spec_reflect.sps"))
        assertTrue(glass("gta_glass_emissivenight.sps"))
        assertTrue(glass("GTA_GLASS_SPEC.SPS"), "the preset is compared without case")
    }

    @Test
    fun `the other presets are not glass`() {
        assertFalse(glass("gta_alpha.sps"), "a blended material is not a pane and keeps blending")
        assertFalse(glass("gta_cutout.sps"))
        assertFalse(glass("gta_normal_spec.sps"))
        assertFalse(glass("gta_default.sps"))
        assertFalse(glass(""))
        assertFalse(glass(null))
    }

    @Test
    fun `a cut-out is not a glass`() {
        val shader = Shader()
        shader.shaderSps = "gta_cutout.sps"

        assertTrue(shader.isCutout)
        assertFalse(shader.isGlass)
    }

    private fun transparent(sps: String?): Boolean {
        val shader = Shader()
        shader.shaderSps = sps
        return shader.isTransparent
    }

    @Test
    fun `the transparent pass draws the glass presets`() {
        assertTrue(transparent("gta_glass_spec.sps"))
        assertTrue(transparent("gta_glass_reflect.sps"))
        assertTrue(transparent("gta_glass_normal_spec_reflect.sps"))
        assertTrue(transparent("GTA_GLASS_EMISSIVENIGHT.SPS"), "the preset is compared without case")
    }

    @Test
    fun `nothing else is drawn in the transparent pass`() {
        assertFalse(transparent("gta_alpha.sps"),
            "the *_alpha presets keep blending where they are until that group is measured")
        assertFalse(transparent("gta_normal_alpha.sps"))
        assertFalse(transparent("gta_decal.sps"))
        assertFalse(transparent("gta_cutout.sps"))
        assertFalse(transparent("gta_normal_spec.sps"))
        assertFalse(transparent("gta_default.sps"))
        assertFalse(transparent(""))
        assertFalse(transparent(null))
    }

    @Test
    fun `only an element with glass asks for the second pass`() {
        val opaque = Element()
        opaque.createShader(listOf("alumox"), null, "gta_normal_spec.sps")
        assertFalse(opaque.hasTransparentMaterials(), "an opaque model needs no glass display list")

        val withGlass = Element()
        withGlass.createShader(listOf("bt1_03_rsn_rls_brick"), null, "gta_normal_spec.sps")
        withGlass.createShader(listOf("bx_glass"), null, "gta_glass_reflect.sps")
        assertTrue(withGlass.hasTransparentMaterials())
    }
}
