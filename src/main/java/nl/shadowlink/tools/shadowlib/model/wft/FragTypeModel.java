package nl.shadowlink.tools.shadowlib.model.wft;

import nl.shadowlink.tools.io.ByteReader;
import nl.shadowlink.tools.shadowlib.model.wdr.DrawableModel;
import nl.shadowlink.tools.shadowlib.model.collections.PtrCollection;

import java.util.LinkedList;

/**
 * A GTA: IV fragment type (`.wft`) - the format of the objects that fall apart: fences, railings,
 * scaffold covers, water bars, electrical boxes, tanks, containers.
 *
 * The resource holds one **main drawable** that carries the shader group (and the embedded texture
 * dictionary) but no geometry of its own, plus a list of `fragTypeChild` structures whose own drawables
 * hold the pieces. A piece writes `shaderGroupOffset = 0`: its geometries index the shaders of the main
 * drawable through `ShaderMappings`, so the group is inherited here and the fragments share one element.
 *
 * Measured on `cj_fence_4_1.wft` (`fences.img`, 24 KB of system memory): the main drawable sits at
 * `0x4000` with its shader group at `0x4E10` and *no* LOD offsets at all, the child list at `0x52C0`
 * holds seven pointers (the list ends where the uninitialised `0xCDCDCDCD` memory begins), and every
 * child has its drawable 0x90 bytes into its structure: `0x4000` + 0x90 -> `0x41E0`, `0x1840` + 0x90 ->
 * `0x18D0` and so on. Each of those drawables has one LOD collection with one model.
 *
 * The reader never calls `readOffset()` on a word it has not looked at: an offset that is not a tagged
 * pointer is a fact about the file (the list ended, the field is unused), not an exception.
 */
public class FragTypeModel {

    /** how many children a walk may read before it stops - a guard, the format has no such limit */
    private static final int MAX_CHILDREN = 64;

    /** `0xB0`: the `pack:/<name>` string, `0xB4`: the main drawable, `0xD4`: the child list */
    private static final int OFFSET_PACK_NAME = 0xB0;
    private static final int OFFSET_MAIN_DRAWABLE = 0xB4;
    private static final int OFFSET_NAME_STRUCTURE = 0xCC;
    private static final int OFFSET_CHILD_LIST = 0xD4;

    /** the fixed header of the structure, so a short resource is refused instead of read past its end */
    private static final int HEADER_LENGTH = OFFSET_CHILD_LIST + 4;

    public int VTable;
    public int blockMapAdress;

    /** the `pack:/<name>` string of the fragment */
    public String packName;

    /** the fragment's own name (`CJ_FENCE_4_1`), null when the structure does not hold one */
    public String name;

    /** the drawable that carries the shader group and the embedded dictionary, no geometry */
    public DrawableModel mainDrawable;

    /** the drawables of the `fragTypeChild` entries - here is the geometry */
    public final LinkedList<DrawableModel> childDrawables = new LinkedList<>();

    /**
     * The placement of each piece, parallel to [childDrawables] (see `FragTypeChild.transform`): the
     * geometry of a piece is in its own local space around the origin, and this is what moves it into
     * the fragment.
     */
    public final LinkedList<float[]> childTransforms = new LinkedList<>();

    /** children that could not be read at all (a broken one must not lose the whole fragment) */
    public int failedChildren = 0;

    /**
     * Children that are there but hold no geometry - the bone chain of a fragment that has one mesh for
     * all its pieces. `cj_fence_4_1` lists seven children and only one of them carries anything to draw.
     */
    public int childrenWithoutGeometry = 0;

    /** children the file lists, before the walk skipped any of them (for the log) */
    public int listedChildren = 0;

    /** where the child list sits in the resource (for the diagnostics of the probes) */
    public int childListOffset = 0;

    public void read(ByteReader br) {
        byte[] buffer = br.toArray();
        if (buffer.length < HEADER_LENGTH) {
            throw new IllegalStateException("Fragment type of " + buffer.length + " bytes is too small");
        }
        VTable = wordAt(buffer, 0);
        blockMapAdress = pointerAt(buffer, 4);

        packName = stringAt(buffer, pointerAt(buffer, OFFSET_PACK_NAME));
        mainDrawable = drawableAt(br, buffer, pointerAt(buffer, OFFSET_MAIN_DRAWABLE));

        // the name structure holds a pointer to the name string as its first word
        int nameStructure = pointerAt(buffer, OFFSET_NAME_STRUCTURE);
        name = stringAt(buffer, pointerAt(buffer, nameStructure));

        childListOffset = pointerAt(buffer, OFFSET_CHILD_LIST);
        readChildren(br, buffer, childListOffset);
    }

    /** Every child the list holds, read one by one; a child that does not parse is counted, not fatal. */
    private void readChildren(ByteReader br, byte[] buffer, int listOffset) {
        if (listOffset <= 0) {
            return;
        }
        while (listedChildren < MAX_CHILDREN) {
            int offset = pointerAt(buffer, listOffset + listedChildren * 4);
            if (offset <= 0) {
                // the list ends here: the words behind it are uninitialised memory, not another child
                break;
            }
            listedChildren++;
            try {
                FragTypeChild child = new FragTypeChild(buffer, offset);
                if (child.drawableOffset <= 0) {
                    failedChildren++;
                    continue;
                }
                DrawableModel drawable = drawableAt(br, buffer, child.drawableOffset);
                if (drawable == null) {
                    failedChildren++;
                    continue;
                }
                if (!hasGeometry(drawable)) {
                    childrenWithoutGeometry++;
                    continue;
                }
                childDrawables.add(drawable);
                childTransforms.add(child.transform);
            } catch (Throwable t) {
                failedChildren++;
            }
        }
    }

    /** whether a piece drawable brought something to draw (a piece without geometry is not one) */
    private boolean hasGeometry(DrawableModel drawable) {
        if (drawable.mModelCollection == null) {
            return false;
        }
        for (PtrCollection<?> collection : drawable.mModelCollection) {
            if (collection != null && !collection._items.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** the drawable at an offset, or null when there is none */
    private DrawableModel drawableAt(ByteReader br, byte[] buffer, int offset) {
        if (offset <= 0 || offset + 4 > buffer.length) {
            return null;
        }
        br.setCurrentOffset(offset);
        DrawableModel drawable = new DrawableModel();
        drawable.readSystemMemory(br);
        return drawable;
    }

    /** the null terminated string at an offset, or null when the offset is not inside the buffer */
    private String stringAt(byte[] buffer, int offset) {
        if (offset <= 0 || offset >= buffer.length) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (int i = offset; i < buffer.length; i++) {
            int character = buffer[i] & 0xff;
            if (character == 0) {
                return text.toString();
            }
            text.append((char) character);
        }
        return text.toString();
    }

    /** the word at an offset, -1 when it lies outside the buffer; never throws */
    private int wordAt(byte[] buffer, int offset) {
        if (offset < 0 || offset + 4 > buffer.length) {
            return -1;
        }
        return (buffer[offset] & 0xff) | ((buffer[offset + 1] & 0xff) << 8)
            | ((buffer[offset + 2] & 0xff) << 16) | ((buffer[offset + 3] & 0xff) << 24);
    }

    /** the offset a pointer word holds, 0 when the word is not a system memory pointer */
    private int pointerAt(byte[] buffer, int at) {
        int word = wordAt(buffer, at);
        if (word == 0 || (word >>> 28) != 5) {
            return 0;
        }
        return word & 0x0fffffff;
    }
}
