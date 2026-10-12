

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
