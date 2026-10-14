package nl.shadowlink.tools.shadowlib.model.model;

import com.jogamp.opengl.GL2;
import nl.shadowlink.tools.io.ByteReader;
import nl.shadowlink.tools.io.ReadFunctions;
import nl.shadowlink.tools.io.Vector4D;
import nl.shadowlink.tools.io.WriteFunctions;
import nl.shadowlink.tools.shadowlib.model.dff.LoadDFF;
import nl.shadowlink.tools.shadowlib.model.dff.NewConvert;
import nl.shadowlink.tools.shadowlib.model.wdr.*;
import nl.shadowlink.tools.shadowlib.model.wft.FragTypeChild;
import nl.shadowlink.tools.shadowlib.model.wft.FragTypeModel;
import nl.shadowlink.tools.shadowlib.model.wft.WftPiecePose;
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic;
import nl.shadowlink.tools.shadowlib.utils.GameType;
import nl.shadowlink.tools.shadowlib.utils.hashing.OneAtATimeHasher;

import java.util.ArrayList;

/**
 * This class contains all the information for one model
 *
 * @author Shadow-Link
 */
public class Model {
    private ArrayList<Element> elements = new ArrayList<Element>();
    public Vector4D center;
    public Vector4D boundsMin;
    public Vector4D boundsMax;

    private boolean loaded = false;
    public int flags;
    public int size;

    private int sysSize = 0;

    public Model() {
    }

    public void attachTXD(TextureDic txd) {
        for (int i = 0; i < elements.size(); i++) {
            elements.get(i).attachTXD(txd);
        }
    }

    /**
     * Resolves the texture every shader asks for against the dictionaries attached to the elements and
     * stores the GL texture id in the shader. Has to run with the GL context current, before the
     * display lists are compiled.
     *
     * @param resolver supplies the GL ids (see GlTextureCache in Shadow-Mapper)
     */
    public void bindTextures(TextureResolver resolver) {
        for (int i = 0; i < elements.size(); i++) {
            elements.get(i).bindTextures(resolver);
        }
    }

    /**
     * Load a WDD file
     *
     * @param img      Bytereader that contains the wdd file, current offset should be at the start of the wdd
     * @param fileSize Size of the file to be loaded
     * @param WDD      Current WDR that needs to be loaded from the WDD
     * @return model
     */
    public Model loadWDD(ByteReader img, int fileSize, String WDD) {
        this.reset();

        byte stream[];

        ResourceFile res = new ResourceFile();
        stream = res.read(img, fileSize);

        int sysSize = res.getSystemSize();

        ByteReader br = new ByteReader(stream, 0);

        long vtable = br.readUInt32();
        long blockMapAddress = br.readOffset();

        long unknown0 = br.readUInt32();
        long unknown1 = br.readUInt32();
        //Message.displayMsgLow("0: " + unknown0);
        // Message.displayMsgLow("1: " + unknown1);

        int hashOffset = br.readOffset();
        int hashCount = br.readUInt16();
        // Message.displayMsgLow("Hashes Offset: "
        // + Utils.getHexString(hashOffset));
        // Message.displayMsgLow("Count: " + hashCount);
        int size = br.readUInt16();

        int save = br.getCurrentOffset();

        br.setCurrentOffset(hashOffset);

        long wddHash = OneAtATimeHasher.getHashKey(WDD);
        long[] wdrHashes = new long[hashCount];

        for (int i = 0; i < hashCount; i++) {
            wdrHashes[i] = br.unsignedInt();
        }

        br.setCurrentOffset(save);

        int drawPOffset = br.readOffset();
        // //Message.displayMsgLow("Drawable Offset: " +
        // Utils.getHexString(drawPOffset));
        int drawPCount = br.readUInt16();
        int drawPSize = br.readUInt16();

        br.setCurrentOffset(drawPOffset);

        int[] wdrOffsets = new int[hashCount];

        for (int i = 0; i < hashCount; i++) {
            wdrOffsets[i] = br.readOffset();
        }

        int j = 0;
        while (wdrHashes[j] != wddHash) {
            j++;
        }
        // Message.displayMsgLow("WDD: " + wdrNames[j] + " "
        // + Utils.getHexString(wdrOffsets[j]));
        br.setCurrentOffset(wdrOffsets[j]);

        DrawableModel sys = new DrawableModel();
        sys.readSystemMemory(br);

        Element element = new Element();
        createModelFromDrawable(element, sys, sysSize, stream, br);
        elements.add(element);

        loaded = true;

        return this;
    }

    /**
     * Creates a model from WDR
     *
     * @param element Element to store this WDR in
     * @param sys     The drawable
     * @param sysSize Size of the system memory
     * @param stream  Stream
     * @param br      ByteReader
     */
    private void createModelFromDrawable(Element element, DrawableModel sys, int sysSize, byte[] stream, ByteReader br) {
        createShadersFromDrawable(element, sys);
        addDrawableGeometry(element, sys, sysSize, stream);
        attachEmbeddedDictionary(element, sys, sysSize, br);
    }

    /**
     * The shader list of a drawable, in group order: one entry per shader of the group, because a
     * geometry's `ShaderMapping` indexes this list - entries must not be dropped when a shader has no
     * texture, or every shader behind it moves.
     */
    private void createShadersFromDrawable(Element element, DrawableModel sys) {
        if (sys.shaderGroup == null) {
            return;
        }
        for (int i = 0; i < sys.shaderGroup.Shaders.Count; i++) {
            ShaderFx shaderFx = sys.shaderGroup.Shaders._items.get(i);
            java.util.List<String> textureNames = shaderFx.shader.getTextureNames();
            // the preset ("gta_trees.sps", "gta_cutout.sps", ...) travels with the shader as well: it is
            // what tells the renderer to cut this material up with an alpha test instead of blending it
            element.createShader(textureNames,
                textureNames.isEmpty() ? shaderFx.shader.describeParams() : null,
                shaderFx.ShaderSPS);
        }
    }

    /**
     * The geometry of a drawable (level of detail 0) as strips on the element.
     *
     * The strip a geometry goes into is the one `createStrip` just appended - **not** the geometry's own
     * index: one element collects the pieces of a whole fragment, the geometry index restarts at 0 for
     * every piece while the strips keep being appended, and passing the geometry index routed every
     * piece behind the first into the first piece's strips (`cj_gb_bench_2`: 30 strips, 27 of them empty,
     * the other three a tangle of every piece's vertices).
     *
     * The vertices are read exactly as the file holds them: a fragment's pieces are already in the
     * fragment's own space (see the note in `createModelFromFragType`), and a `.wdr`'s geometry always is.
     */
    private void addDrawableGeometry(Element element, DrawableModel sys, int sysSize, byte[] stream) {
        addDrawableGeometry(element, sys, sysSize, stream, null);
    }

    /**
     * The same, with a piece's frame applied to its positions when `pose` is not null (a fragment's piece).
     */
    private void addDrawableGeometry(Element element, DrawableModel sys, int sysSize, byte[] stream, float[] pose) {
        if (sys.mModelCollection == null || sys.mModelCollection.length == 0
                || sys.mModelCollection[0]._items.isEmpty()) {
            return;
        }
        ModelFile mf = new ModelFile();
        Model2 test = sys.mModelCollection[0]._items.get(0);// load all
        // geometries
        // into one
        // model
        for (int i = 0; i < test.Geometries._items.size(); i++) {
            Geometry geo = test.Geometries._items.get(i);
            // which shader the geometry uses comes from the model's ShaderMappings: the position
            // of the geometry in the list is only the same by coincidence (in NJ_01 that is not the
            // case for 918 of 4455 geometries, and those were textured with the wrong shader)
            int shaderIndex = i;
            if (test.ShaderMappings != null && test.ShaderMappings.Count > i) {
                shaderIndex = test.ShaderMappings.Values.get(i);
            }
            element.createStrip(geo.FaceCount, shaderIndex);
            int stripIndex = element.getStripCount() - 1;
            // //Message.displayMsgLow("Adding vertices");

            mf.addVertices(element, stream, geo.vertexBuffer.DataOffset + sysSize, geo.indexBuffer.DataOffset + sysSize, geo.VertexCount,
                    geo.FaceCount, geo.VertexStride, stripIndex, pose);
            // //Message.displayMsgLow("Adding polygons");
            mf.addPolygons(element, stream, geo.vertexBuffer.DataOffset + sysSize, geo.indexBuffer.DataOffset + sysSize, geo.VertexCount,
                    geo.FaceCount, geo.VertexStride, stripIndex);
        }
    }

    /** The dictionary embedded in the drawable, when it carries one. */
    private void attachEmbeddedDictionary(Element element, DrawableModel sys, int sysSize, ByteReader br) {
        if (sys.shaderGroup == null || sys.shaderGroup.TextureDictionaryOffset == 0) {
            return;
        }
        br.setCurrentOffset(sys.shaderGroup.TextureDictionaryOffset);
        TextureDic wtd = new TextureDic("embed", br, GameType.GTA_IV, false, sysSize);
        element.attachTXD(wtd);
    }

    /**
     * Loads a WDR file
     *
     * @param br       ByteReader that contains the WDR file, current offset should be set to start of WDR
     * @param fileSize Size of the file
     * @return this
     */
    public Model loadWDR(ByteReader br, int fileSize) {
        this.reset();

        byte stream[];

        DrawableModel sys = new DrawableModel();

        ResourceFile res = new ResourceFile();
        stream = res.read(br, fileSize);

        sysSize = res.getSystemSize();

        ByteReader br2 = new ByteReader(stream, 0);
        sys.readSystemMemory(br2);
        center = sys.Center;
        boundsMin = sys.BoundsMin;
        boundsMax = sys.BoundsMax;

        Element element = new Element();
        createModelFromDrawable(element, sys, sysSize, stream, br2);
        elements.add(element);
        element = null;

        loaded = true;

        return this;
    }

    public DrawableModel loadWDRSystem(ByteReader br, int fileSize) {
        this.reset();

        byte stream[];

        DrawableModel sys = new DrawableModel();

        ResourceFile res = new ResourceFile();
        stream = res.read(br, fileSize);

        ByteReader br2 = new ByteReader(stream, 0);
        sys.readSystemMemory(br2);
        center = sys.Center;
        boundsMin = sys.BoundsMin;
        boundsMax = sys.BoundsMax;

        return sys;
    }

    /**
     * Loads a WFT file
     *
     * @param br       ByteReader that contains the WFT file, current offset should be set to start of WFT
     * @param fileSize Size of the file
     * @return this
     */
    public Model loadWFT(ByteReader br, int fileSize) {
        this.reset();

        byte stream[];

        ResourceFile res = new ResourceFile();
        stream = res.read(br, fileSize);

        sysSize = res.getSystemSize();

        ByteReader br2 = new ByteReader(stream, 0);

        FragTypeModel wft = new FragTypeModel();
        wft.read(br2);

        createModelFromFragType(wft, sysSize, stream, br2);

        loaded = true;

        return this;
    }

    /**
     * Created drawables from WFT
     *
     * @param wft     the wft
     * @param sysSize System size
     * @param stream  Stream
     * @param br      Bytereader
     */
    private void createModelFromFragType(FragTypeModel wft, int sysSize, byte[] stream, ByteReader br) {
        if (wft.mainDrawable == null) {
            throw new IllegalStateException("Fragment type without a drawable");
        }
        // One element for the whole fragment: its own drawable carries the shader group and the
        // embedded dictionary, every piece brings geometry, and a piece's ShaderMappings index the
        // shaders of that one group (a fragment has no shader group of its own, shaderGroupOffset is 0).
        Element element = new Element();
        createShadersFromDrawable(element, wft.mainDrawable);
        attachEmbeddedDictionary(element, wft.mainDrawable, sysSize, br);
        // some fragments carry the whole mesh in their own drawable (the furniture and the cutscene
        // characters do), others have it in the pieces only - the fence has an empty main drawable
        addDrawableGeometry(element, wft.mainDrawable, sysSize, stream);
        // The pieces: their geometry is read exactly as the file holds it unless the piece's own frame says
        // otherwise - see `WftPiecePose`, which owns the rule and its measurement. `-Dsm.wft.pose=raw|
        // inverse|rule` forces one behaviour for an A/B frame.
        String poseMode = WftPiecePose.mode();
        for (int i = 0; i < wft.childDrawables.size(); i++) {
            DrawableModel piece = wft.childDrawables.get(i);
            FragTypeChild frame = i < wft.childFrames.size() ? wft.childFrames.get(i) : null;
            addDrawableGeometry(element, piece, sysSize, stream, WftPiecePose.poseFor(poseMode, frame, piece));
        }
        elements.add(element);
    }

    /**
     * Load a WDD file from File
     *
     * @param file
     *        String to the file
     * @param gl
     *        used to load textures
     * @param wdrName
     *        Name of the WDR
     * @return this
     */
    /* public Model loadWDD(String file, GL gl, String wdrName){ this.reset(); ReadFunctions rf = new ReadFunctions();
     * rf.openFile(file); ByteReader br = rf.getByteReader(); loadWDD(br, -1, gl, wdrName); loaded = true; return this;
     * } */

    /**
     * Load WDR file from file
     *
     * @param file String to file to load
     * @return this
     */
    public Model loadWDR(String file) {
        this.reset();
        ReadFunctions rf = new ReadFunctions(file);
        ByteReader br = rf.getByteReader(rf.moreToRead());
        loadWDR(br, -1);
        loaded = true;
        return this;
    }

    /**
     * Load WFT file from file
     *
     * @param file
     *        String to file
     * @param gl
     *        used for embedded textures
     * @return this
     */
    /* public Model loadWFT(String file, GL gl){ this.reset(); ReadFunctions rf = new ReadFunctions();
     * rf.openFile(file); ByteReader br = rf.getByteReader(); loadWFT(br, -1, gl); loaded = true; return this; } */

    /**
     * Load DFF from file
     *
     * @param file String to file
     * @return this
     */
    public Model loadDFF(String file) {
        ReadFunctions rf = new ReadFunctions(file);
        ByteReader br = rf.getByteReader();
        loadDFF(br);
        loaded = true;
        return this;
    }

    /**
     * Load DFF from byteReader
     *
     * @param br ByteReader that contains the dff, offset should be set to start of dff
     * @return this
     */
    public Model loadDFF(ByteReader br) {
        this.reset();
        Element element = new Element();
        new LoadDFF(br, element);
        elements.add(element);
        loaded = true;
        return this;
    }

    /**
     * Checks if the model is loaded
     *
     * @return true if this model is loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Set true if this model finished loading
     *
     * @param loaded true if this model finished loading
     */
    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    /**
     * Render this model
     *
     * @param gl used to render this model
     */
    public void render(GL2 gl) {
        for (int i = 0; i < elements.size(); i++) {
            elements.get(i).render(gl);
        }
    }

    /**
     * Render everything of this model that is not a transparent material - the renderer's first pass.
     *
     * @param gl the context
     */
    public void renderOpaque(GL2 gl) {
        for (int i = 0; i < elements.size(); i++) {
            elements.get(i).renderOpaque(gl);
        }
    }

    /**
     * Render only the transparent materials of this model - the renderer's second, blended pass.
     *
     * @param gl the context
     */
    public void renderTransparent(GL2 gl) {
        for (int i = 0; i < elements.size(); i++) {
            elements.get(i).renderTransparent(gl);
        }
    }

    /**
     * True when this model has a material the transparent pass draws, i.e. it needs a second display list.
     *
     * @return true when any element of this model has a transparent material
     */
    public boolean hasTransparentMaterials() {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).hasTransparentMaterials()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reset this model
     */
    public void reset() {
        elements.clear();
        loaded = false;
    }

    public Element getElement(int id) {
        if (elements.size() > id) {
            return elements.get(id);
        } else {
            return null;
        }
    }

    public void convertToWDR(WriteFunctions wf) {
        new NewConvert(this, wf);
    }

    public void createElement() {
        elements.add(new Element());
    }

}
