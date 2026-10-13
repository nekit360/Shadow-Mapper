

package nl.shadowlink.tools.shadowlib.model.model;

/**
 *
 * @author Shadow-Link
 */
public class Shader {
    private String textureName = "";
    private int glTex;

    /** every name this shader carries for its diffuse map, the preferred one first (see the .wdr Shader) */
    private java.util.List<String> textureNames = new java.util.ArrayList<String>();

    /** the parameters of the .wdr shader this one was made from, for the log (empty when unknown) */
    private String params = "";

    /**
     * The shader preset of the .wdr shader this one was made from - "gta_trees.sps", "gta_cutout.sps",
     * "gta_normal.sps", "gta_alpha.sps", ... It is what says how the material has to be drawn: a cut-out
     * preset means the alpha channel of the diffuse map decides which texels exist at all, while an
     * `_alpha` preset is a blended material (glass, decals).
     */
    private String shaderSps = "";

    /**
     * @return the preset of the .wdr shader, e.g. "gta_trees.sps"; empty when the model was made from a
     *         format that has no preset (a .dff, for instance)
     */
    public String getShaderSps() {
        return shaderSps;
    }

    /**
     * @param shaderSps the preset of the .wdr shader, see {@link #getShaderSps()}
     */
    public void setShaderSps(String shaderSps) {
        this.shaderSps = shaderSps == null ? "" : shaderSps;
    }

    /**
     * True when this material is a cut-out: GTA: IV draws those with an alpha test, so a texel below the
     * threshold is not drawn at all (leaves, bare branches, fences, grates, signs). Measured over the map,
     * the presets that mean this are `gta_cutout.sps` and `gta_trees.sps` (foliage), while
     * `gta_alpha.sps` and the `*_alpha` presets are the blended ones (glass, decals) and must stay blended.
     *
     * @return true when the alpha channel of the diffuse map cuts this material up
     */
    public boolean isCutout() {
        String sps = shaderSps.toLowerCase();
        return sps.contains("cutout") || sps.contains("tree") || sps.contains("foliage");
    }

    /**
     * True when this material is a **glass** surface (`gta_glass_spec.sps`, `gta_glass_reflect.sps`,
     * `gta_glass_normal_spec_reflect.sps`, `gta_glass_emissivenight.sps`, ...).
     *
     * The alpha channel of such a pane is the transparency of the glass itself, and the game blends it: 29 % to
     * 58 % mean alpha measured on the panes of the modded district, i.e. most of what stands behind the pane
     * shows through. In the game there is something behind it - the interior, or the world - so the pane reads
     * as glazing; in the editor there is nothing but the colour the view was cleared with, which is why a
     * blended pane used to read as a hole. Such a material is therefore drawn in the renderer's **transparent
     * pass** (see {@link #isTransparent()}), where it blends against what the opaque pass left behind.
     *
     * @return true when this material is a glass surface
     */
    public boolean isGlass() {
        return shaderSps.toLowerCase().contains("glass");
    }

    /**
     * True when this material belongs in the renderer's **transparent pass**: it is drawn after every opaque
     * material of the frame, blended with its own alpha and sorted back to front, so that what stands behind it
     * - the interior of a building, the wall behind a pane - is what it blends against instead of the clear
     * colour. Taken out of that pass, a blended material either blends against a half-drawn frame (the clear
     * colour pouring through a window) or, drawn opaque, shows its own texture and nothing else.
     *
     * The rule, measured over `unost_square.img` and `nj_01.img` (see `STATUS.md`):
     *
     * * **glass** ({@link #isGlass()}) always - 6 shader slots on 4 models in the mod's district;
     * * an **alpha preset** (`gta_alpha.sps`, `gta_normal_alpha.sps`, `gta_emissive_alpha.sps`, ...):
     *   the name says the material is blended by design. In `nj_01.img` that is 41 slots, most of them the
     *   district's glass (`NJ01_GLASS`) and its night windows and neon;
     * * a material whose **diffuse map carries transparency**, whatever its preset claims: the mod's glass
     *   doors (`td_glass_door_l_DefaultMaterial_BaseColor`, 32 % of its texels below 0.5 alpha under a generic
     *   `gta_default.sps`) and `prop_green_glassprop_green_glass_a` (100 %) are exported that way;
     * * **not** a **cut-out** ({@link #isCutout()}): its alpha channel is a mask, and the alpha test that draws
     *   it is what a leaf or a fence needs - blending it would wash it out (that is item 11 of `NEXT-STEPS.md`);
     * * **not** a **decal** ({@link #isDecalPreset()}): a decal is coplanar with the surface it lies on (the
     *   road markings measure 86 %, the rust stains 59 % of their texels below 0.5), so moving it behind that
     *   surface costs it the depth test - in `nj_01.img` that is 190 slots of graffiti, road markings, shadows
     *   and posters, and they keep the path that works for them today.
     *
     * @return true when the transparent pass draws this material
     */
    public boolean isTransparent() {
        if (isCutout() || isDecalPreset()) {
            return false;
        }
        return isGlass() || isAlphaPreset() || textureHasAlpha;
    }

    /**
     * True when the preset of this material is one of the `*_alpha` family - `gta_alpha.sps`,
     * `gta_normal_alpha.sps`, `gta_spec_alpha.sps`, `gta_emissive_alpha.sps`, `gta_emissivenight_alpha.sps`,
     * `gta_normal_spec_alpha.sps`: the materials the game blends by design, glass and windows and neon.
     *
     * @return true when the preset marks this material as blended
     */
    public boolean isAlphaPreset() {
        return shaderSps.toLowerCase().contains("alpha");
    }

    /**
     * True when the preset of this material marks it as a **decal**: `gta_decal.sps`, `gta_decal_glue.sps`,
     * `gta_normal_decal.sps`, `gta_spec_decal.sps`. A decal is a quad that lies *on* a surface (road markings,
     * graffiti, rust, shadows, posters - 190 slots in `nj_01.img`, alpha 22-98 %), so it belongs in the pass
     * that draws it immediately after the surface it belongs to, not in the transparent pass at the end of the
     * frame, where it would lose the depth test against that surface.
     *
     * @return true when this material is a decal
     */
    public boolean isDecalPreset() {
        return shaderSps.toLowerCase().contains("decal");
    }

    /**
     * Whether the diffuse map this shader resolved uses its alpha channel as transparency (see
     * {@code Texture.alpha()}). Set while the textures are bound, before the renderer asks
     * {@link #isTransparent()}.
     *
     * @param textureHasAlpha true when the resolved diffuse map has a transparent alpha channel
     */
    public void setTextureHasAlpha(boolean textureHasAlpha) {
        this.textureHasAlpha = textureHasAlpha;
    }

    /**
     * @return true when the diffuse map of this material carries a transparent alpha channel
     */
    public boolean hasTransparentTexture() {
        return textureHasAlpha;
    }

    /** set while the textures are bound: the resolved diffuse map uses its alpha as transparency */
    private boolean textureHasAlpha = false;

    /**
     * Create an empty shader
     */
    public Shader(){
    }

    /**
     * The names of the diffuse maps of the .wdr shader behind this one, the preferred one first: a model's
     * two shaders can name the same texture under different parameters, and the name may be in only one of
     * the dictionaries the model has, so the renderer tries them in order.
     *
     * @return the names, empty when the shader names no map at all
     */
    public java.util.List<String> getTextureNames() {
        return textureNames;
    }

    /**
     * @param names the diffuse map names of the .wdr shader, see {@link #getTextureNames()}
     */
    public void setTextureNames(java.util.List<String> names) {
        textureNames = names == null ? new java.util.ArrayList<String>() : names;
        textureName = textureNames.isEmpty() ? "" : textureNames.get(0);
    }

    /**
     * The parameters of the .wdr shader behind this one, as `hash(type)` - the log uses it to show which
     * parameters a shader carries when it has no diffuse map under "TextureSampler".
     *
     * @return the description, or an empty string when it was not set
     */
    public String getParams() {
        return params;
    }

    /**
     * @param params the description of the .wdr shader, see {@link #getParams()}
     */
    public void setParams(String params) {
        this.params = params == null ? "" : params;
    }

    /**
     * Returns the name of this texture
     * @return name of this texture
     */
    public String getTextureName() {
        return textureName;
    }

    /**
     * Set the name of the texture inside this shader
     * @param textureName name of the texture
     */
    public void setTextureName(String textureName) {
        this.textureName = textureName == null ? "" : textureName;
        textureNames = new java.util.ArrayList<String>();
        if (!this.textureName.isEmpty()) {
            textureNames.add(this.textureName);
        }
    }

    /**
     * gl int of the tex
     * @param glTex gl int of the tex
     */
    public void setGLTex(int glTex){
        this.glTex = glTex;
    }

    /**
     * Returns the GL tex of this shader
     * @return the GL tex of this shader
     */
    public int getGLTex(){
        return glTex;
    }

}
