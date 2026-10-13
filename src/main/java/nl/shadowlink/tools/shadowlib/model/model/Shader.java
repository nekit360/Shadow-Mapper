

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
     * colour. Taken out of that pass, a pane either shows its own texture flat (drawn opaque) or turns the
     * whole window wall into a hole (blended wherever the draw order happens to put it).
     *
     * Today this is the glass presets ({@link #isGlass()}). The `*_alpha` presets (`gta_alpha.sps`,
     * `gta_normal_alpha.sps`, `gta_spec_alpha.sps`, ..., decals, and the mod's own glass under a generic
     * `gta_default.sps` preset) are the candidates to join them, but they keep the old behaviour until that
     * group is measured.
     *
     * @return true when the transparent pass draws this material
     */
    public boolean isTransparent() {
        return isGlass();
    }

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
