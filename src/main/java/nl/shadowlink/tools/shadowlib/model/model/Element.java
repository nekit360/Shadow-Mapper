package nl.shadowlink.tools.shadowlib.model.model;

import com.jogamp.opengl.GL2;
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic;

import java.util.ArrayList;

/**
 * @author Shadow-Link
 */
public class Element {

    private ArrayList<Strip> strips = new ArrayList();
    private ArrayList<Vertex> vert = new ArrayList();
    private ArrayList<Shader> shaders = new ArrayList();
    /** Dictionaries offered by attachTXD, tried in that order when a texture name is resolved. */
    private ArrayList<TextureDic> textureDictionaries = new ArrayList();

    private Vertex max = new Vertex(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    private Vertex min = new Vertex(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    private Vertex center = new Vertex(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

    private boolean hasVertexColors = false;
    private boolean hasNormals = false;

    private int polygons;
    private int vertices;

    private boolean visible = true;

    /**
     * Get the shader from the list of shaders
     *
     * @param id of the shader you want
     * @return the requested shader
     */
    public Shader getShader(int i) {
        return shaders.get(i);
    }

    /**
     * Created a new empty element
     */
    public Element() {
    }

    /**
     * Attaches a TXD to this element.
     *
     * The textures are only uploaded and matched to the shaders by
     * {@link #bindTextures(TextureResolver)}: in GTA: IV a shader names its diffuse map by name, and
     * that name can live either in the model's own (embedded) dictionary or in the TXD of the .ide
     * entry, so both have to be offered before anything can be bound.
     */
    public void attachTXD(TextureDic txd) {
        // TODO: Why can this be null?
        if (txd == null) {
            return;
        }

        textureDictionaries.add(txd);
    }

    /**
     * Resolves the texture name of every shader against the attached dictionaries and stores the GL
     * texture id in the shader. Runs before the display list of this model is compiled, with the GL
     * context current.
     *
     * A name that none of the attached dictionaries has is handed to the resolver once more without a
     * dictionary: in GTA: IV the shader only names its texture and the game looks it up in what is
     * loaded, so it can live in a dictionary of another model of the same image.
     *
     * @param resolver supplies the GL texture ids, 0 when a dictionary does not have that name
     */
    public void bindTextures(TextureResolver resolver) {
        for (int i = 0; i < shaders.size(); i++) {
            Shader shader = shaders.get(i);
            int glTex = 0;
            // a shader can name its diffuse map under more than one parameter (a model's two shaders name
            // the same texture under "TextureSampler" and under 0xD52B11DF), and a name may be in only one
            // of the dictionaries the model has: try them in order, not just the first
            java.util.List<String> names = shader.getTextureNames();
            for (int n = 0; n < names.size() && glTex == 0; n++) {
                String textureName = names.get(n);
                for (int d = 0; d < textureDictionaries.size() && glTex == 0; d++) {
                    glTex = resolver.resolve(textureDictionaries.get(d), textureName);
                }
                if (glTex == 0) {
                    glTex = resolver.resolve(null, textureName);
                }
            }
            if (names.isEmpty()) {
                // a shader without a diffuse map draws with the default (white) texture: worth counting,
                // the map is not white where the game is not white either
                shadersWithoutTextureName++;
                if (unknownShaderParams.size() < 8) {
                    unknownShaderParams.add(shader.getParams());
                }
            }
            if (glTex == 0) {
                shadersWithoutTexture++;
            }
            shader.setGLTex(glTex);
        }
    }

    /** shader slots that carry no diffuse map at all (they render with the default texture) */
    public static int shadersWithoutTextureName = 0;

    /** shader slots whose name matched no dictionary */
    public static int shadersWithoutTexture = 0;

    /** the parameters of the first shaders that had no diffuse map, for the log */
    public static final java.util.List<String> unknownShaderParams = new java.util.ArrayList<String>();

    /** Forgets what the last load counted. */
    public static void resetTextureDiagnostics() {
        shadersWithoutTextureName = 0;
        shadersWithoutTexture = 0;
        unknownShaderParams.clear();
    }

    /**
     * Checks if this element contains normals
     *
     * @return true if it has normals
     */
    public boolean hasNormals() {
        return hasNormals;
    }

    /**
     * Sets if this element contains normals
     *
     * @param hasNormals true if it has normals
     */
    public void hasNormals(boolean hasNormals) {
        this.hasNormals = hasNormals;
    }

    /**
     * Create a new empty shader
     */
    public void createShader() {
        shaders.add(new Shader());
    }

    /**
     * Create a new shader with a texturename
     *
     * @param textureName the name of the texture
     */
    public void createShader(String textureName) {
        Shader temp = new Shader();
        temp.setTextureName(textureName == null ? "" : textureName);
        shaders.add(temp);
    }

    /**
     * Create a new shader with a texture name and a description of the .wdr shader it comes from
     *
     * @param textureName the name of the texture
     * @param params      the parameters of the .wdr shader, see {@link Shader#getParams()}
     */
    public void createShader(String textureName, String params) {
        Shader temp = new Shader();
        temp.setTextureName(textureName == null ? "" : textureName);
        temp.setParams(params);
        shaders.add(temp);
    }

    /**
     * Create a new shader from the diffuse map names of a .wdr shader
     *
     * @param textureNames every name the .wdr shader carries for its diffuse map, preferred one first
     * @param params       the parameters of the .wdr shader, see {@link Shader#getParams()}
     * @param shaderSps    the preset of the .wdr shader, see {@link Shader#getShaderSps()}
     */
    public void createShader(java.util.List<String> textureNames, String params, String shaderSps) {
        Shader temp = new Shader();
        temp.setTextureNames(textureNames);
        temp.setParams(params);
        temp.setShaderSps(shaderSps);
        shaders.add(temp);
    }

    /**
     * Returns the amount of shaders in this element
     *
     * @return shader count
     */
    public int getShaderCount() {
        return shaders.size();
    }

    /**
     * Checks if this element should be rendered or not
     *
     * @return true if it should be rendered
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Set if this element should be rendered or not
     *
     * @param visible true if it should be rendered
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * returns the number of trianglestrips used by this element
     *
     * @return Stripcount
     */
    public int getStripCount() {
        return strips.size();
    }

    /**
     * Reset all values of this element: Vertices Strips Shaders hasVertexColors polygon Count Vertex Count Visible
     */
    public void reset() {
        vert.clear();
        strips.clear();
        shaders.clear();
        textureDictionaries.clear();
        hasVertexColors = false;
        polygons = 0;
        vertices = 0;
        visible = false;
    }

    /**
     * Create a new strip with an given amount of polys and an material index
     *
     * @param polyCount     the number of polygons this strip will contain
     * @param materialIndex id of the material used by this strip
     */
    public void createStrip(int polyCount, int materialIndex) {
        strips.add(new Strip(polyCount, materialIndex));
    }

    /**
     * Returns a strip from the strip array
     *
     * @param id id of the strip
     * @return The strip
     */
    public Strip getStrip(int id) {
        return strips.get(id);
    }

    /**
     * Create a new polygon for this model
     *
     * @param a Vertex a
     * @param b Vertex b
     * @param c Vertex c
     */
    public void createModelPoly(int a, int b, int c, int stripIndex, boolean check) {
        // //Message.displayMsgLow("Original poly: " + a + ", " + b + ", " + c);
        if (check) {
            a = strips.get(stripIndex).addVertex(vert.get(a));
            b = strips.get(stripIndex).addVertex(vert.get(b));
            c = strips.get(stripIndex).addVertex(vert.get(c));
        }
        // //Message.displayMsgLow("Created poly: " + a + ", " + b + ", " + c);
        strips.get(stripIndex).addPoly(new Polygon(a, b, c));
    }

    public void addVertexToStrip(float x, float y, float z, float u, float v, int stripIndex) {
        strips.get(stripIndex).addVertexToStrip(new Vertex(x, y, z, u, v));
    }

    /**
     * Create a new vertex for this model
     *
     * @param x X Position of the vertex
     * @param y Y Position of the vertex
     * @param z Z Position of the vertex
     */
    public void createModelVertex(int id, float x, float y, float z, float u, float v) {
        if (vert.size() <= id) {
            vert.add(new Vertex(x, y, z, u, v));
        } else {
            if (u == -1) {
                u = vert.get(id).getVertexU();
                v = vert.get(id).getVertexV();
            }
            vert.set(id, new Vertex(x, y, z, u, v));
        }
        checkBounds(new Vertex(x, y, z, u, v));
    }

    /**
     * Check if the vertex is out of the current bounds if so it will set the bounds of this element to the current
     * vertex
     *
     * @param vertex Vertex to check this with
     */
    public void checkBounds(Vertex vertex) {
        if (vertex.getVertexX() > max.getVertexX())
            max.setVertexX(vertex.getVertexX());
        if (vertex.getVertexY() > max.getVertexY())
            max.setVertexY(vertex.getVertexY());
        if (vertex.getVertexZ() > max.getVertexZ())
            max.setVertexZ(vertex.getVertexZ());
        if (vertex.getVertexX() < min.getVertexX())
            min.setVertexX(vertex.getVertexX());
        if (vertex.getVertexY() < min.getVertexY())
            min.setVertexY(vertex.getVertexY());
        if (vertex.getVertexZ() < min.getVertexZ())
            min.setVertexZ(vertex.getVertexZ());
    }

    /**
     * Calculate the center of this element
     */
    public void createCenter() {
        center.setVertexX((max.getVertexX() + min.getVertexX()) / 2);
        center.setVertexY((max.getVertexY() + min.getVertexY()) / 2);
        center.setVertexZ((max.getVertexZ() + min.getVertexZ()) / 2);
    }

    /**
     * Displays the current bounds and center of this element
     */
    public void displayBounds() {
        /* //Message.displayMsgLow("Max: "); //Message.displayMsgLow(max.getVertexX() + ", " +
         * max.getVertexY() + ", " + max.getVertexZ()); //Message.displayMsgLow("Min: ");
         * //Message.displayMsgLow(min.getVertexX() + ", " + min.getVertexY() + ", " + min.getVertexZ());
         * //Message.displayMsgLow("Center: "); //Message.displayMsgLow(center.getVertexX() + ", " +
         * center.getVertexY() + ", " + center.getVertexZ()); */
    }

    /**
     * Returns the center of this element as vertex
     *
     * @return Vertex with the values of the center
     */
    public Vertex getCenter() {
        return center;
    }

    /**
     * Returns the max of this element as vertex
     *
     * @return Vertex with max values of this element
     */
    public Vertex getMax() {
        return max;
    }

    /**
     * Returns the min of this element as vertex
     *
     * @return Vertex with min values of this element
     */
    public Vertex getMin() {
        return min;
    }

    /**
     * Set the UV Coordinates of a vertex
     *
     * @param id Vertex ID
     * @param u  Vertex U Coordinates
     * @param v  Vertex V Coordinates
     */
    public void setModelUV(int id, float u, float v) {
        Vertex vertx = vert.get(id);
        vertx.setVertexU(u);
        vertx.setVertexV(v);
    }

    /**
     * Returns the U Coordinates of a vertex
     *
     * @param id ID of the vertex
     * @return The U Coordinate of the vertex
     */
    public float getModelMapU(int id) {
        Vertex vertx = vert.get(id);
        return vertx.getVertexU();
    }

    /**
     * Returns the V Coordinates of a vertex
     *
     * @param id ID of the vertex
     * @return The V Coordinate of the vertex
     */
    public float getModelMapV(int id) {
        Vertex vertx = vert.get(id);
        return vertx.getVertexV();
    }

    /**
     * Returns the X Position of the vertex
     *
     * @param id Vertex ID
     * @return the X Position of the vertex
     */
    public float getVertexX(int id) {
        Vertex vertx = vert.get(id);
        return vertx.getVertexX();
    }

    /**
     * Returns the Y Position of the vertex
     *
     * @param id Vertex ID
     * @return the Y Position of the vertex
     */
    public float getVertexY(int id) {
        Vertex vertx = vert.get(id);
        return vertx.getVertexY();
    }

    /**
     * Returns the Z Position of the vertex
     *
     * @param id Vertex ID
     * @return the Z Position of the vertex
     */
    public float getVertexZ(int id) {
        Vertex vertx = vert.get(id);
        return vertx.getVertexZ();
    }

    /**
     * Returns the a vertex of the polygon
     *
     * @param id Polygon ID
     * @return the a vertex of the polygon
     */
    public int getPolygonA(int stripIndex, int id) {
        Polygon polyn = strips.get(stripIndex).getPoly(id);
        return polyn.a;
    }

    /**
     * Returns the b vertex of the polygon
     *
     * @param id Polygon ID
     * @return the b vertex of the polygon
     */
    public int getPolygonB(int stripIndex, int id) {
        Polygon polyn = strips.get(stripIndex).getPoly(id);
        return polyn.b;
    }

    /**
     * Returns the c vertex of the polygon
     *
     * @param id Polygon ID
     * @return the c vertex of the polygon
     */
    public int getPolygonC(int stripIndex, int id) {
        Polygon polyn = strips.get(stripIndex).getPoly(id);
        return polyn.c;
    }

    /**
     * Returns the number of polygons in this model
     *
     * @return the numbers of polygons in this model
     */
    public int getPolygons() {
        return polygons;
    }

    /**
     * Returns the number of vertices in this model
     *
     * @return the numbers of vertices in this model
     */
    public int getVertices() {
        return vertices;
    }

    /**
     * Set number of polygons used in this model
     *
     * @param polygons number of polygons
     */
    public void setPolygons(int polygons) {
        this.polygons = polygons;
    }

    /**
     * Set number of vertices used in this model
     *
     * @param vertices number of vertices
     */
    public void setVertices(int vertices) {
        this.vertices = vertices;
    }

    /**
     * Set if this element contains vertex colors
     *
     * @param vertexColors true if it contains vertex colors
     */
    public void setHasVertexColors(boolean vertexColors) {
        this.hasVertexColors = vertexColors;
    }

    /**
     * Check if this element contains vertex colors
     *
     * @return true if this element contains vertex colors
     */
    public boolean getHasVertexColors() {
        return hasVertexColors;
    }

    /**
     * Returns the vertex specified by id
     *
     * @param id the id of the wanted vertex
     * @return Vertex
     */
    public Vertex getVertex(int id) {
        return vert.get(id);
    }

    /**
     * Returns the polygon specified by id
     *
     * @param stripIndex the strip you want to take this polygon from
     * @param id         the id of the polygon
     * @return polygon
     */
    public Polygon getPolygon(int stripIndex, int id) {
        return strips.get(stripIndex).getPoly(id);
    }

    /**
     * Render this element if visible is true
     *
     * @param gl used to render this element
     */
    public void render(GL2 gl) {
        if (visible) {
            for (int i = 0; i < strips.size(); i++) {
                // bind the texture of the shader this strip uses; the guard used to compare the strip
                // index with the shader count, so a strip beyond the shader list bound the wrong one
                int shaderIndex = strips.get(i).getShaderNumber();
                int glTex = 0;
                Shader stripShader = null;
                if (shaderIndex >= 0 && shaderIndex < shaders.size()) {
                    stripShader = shaders.get(shaderIndex);
                    glTex = stripShader.getGLTex();
                }
                applyAlphaState(gl, stripShader);
                gl.glBindTexture(gl.GL_TEXTURE_2D, glTex);
                strips.get(i).render(gl);
            }
        }
    }

    /**
     * The alpha state of one material, from its shader preset.
     *
     * A cut-out material (leaves, bare branches, fences) is drawn with an **alpha test** and without
     * blending: the alpha channel of the diffuse map says which texels exist. Drawing such a material
     * with blending instead - which is what the renderer did for everything - paints the transparent
     * part of the card as well, so the background shines through the leaves and a tree looks washed
     * out and pale at every distance, and its leaves take on the colour of whatever is behind them.
     *
     * A **glass** material is drawn opaque for the opposite reason: its alpha channel is the transparency
     * of the pane, and behind a window an editor has nothing but the colour the view was cleared with, so
     * a blended pane pours the sky through the building (see {@link Shader#isGlass()}).
     *
     * Everything else keeps the previous behaviour (blending on, no alpha test), which is what the
     * blended materials of the game - decals, the `gta_alpha.sps` presets - are drawn with.
     *
     * @param gl     the context
     * @param shader the shader of the strip, null when the strip has none
     */
    private void applyAlphaState(GL2 gl, Shader shader) {
        if (shader != null && shader.isCutout()) {
            gl.glEnable(GL2.GL_ALPHA_TEST);
            gl.glAlphaFunc(GL2.GL_GREATER, CUTOUT_ALPHA);
            gl.glDisable(GL2.GL_BLEND);
            // ... and without back-face culling, because a cut-out is thin: a leaf is one card, and the
            // card that faces away is part of the same canopy. Culling them hid a quarter of a crown
            // (Tree_Beech1: 220 799 px with culling against 244 273 px without it, same build).
            gl.glDisable(GL2.GL_CULL_FACE);
        } else if (shader != null && shader.isGlass()) {
            gl.glDisable(GL2.GL_ALPHA_TEST);
            // a glass pane keeps its own colour instead of letting the view's clear colour through it ...
            gl.glDisable(GL2.GL_BLEND);
            // ... and it is drawn from both sides, because a pane is a single quad: the window wall of the
            // mod's cinema is 100 % outward-facing (PaneFacingProbe), so from inside the arcade it was culled
            // away and read as an opening with the world behind it, which is what was reported.
            gl.glDisable(GL2.GL_CULL_FACE);
        } else {
            gl.glDisable(GL2.GL_ALPHA_TEST);
            gl.glEnable(GL2.GL_BLEND);
            gl.glEnable(GL2.GL_CULL_FACE);
        }
    }

    /**
     * The alpha a cut-out material keeps a texel at. 0.5 is the value the game's cut-out presets use,
     * and it is the point where a leaf keeps its own colour instead of taking on the background's.
     */
    private static final float CUTOUT_ALPHA = 0.5f;

}
