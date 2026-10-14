package nl.shadowlink.tools.shadowlib.model.wft;

/**
 * One piece of a fragment type: a `fragTypeChild` structure of the resource.
 *
 * Measured on `cj_fence_4_1.wft`: the structure is 0xBC bytes long, starts with a class id and two
 * floats, carries eight vectors, and holds the pointer to the piece's own drawable 0x90 bytes in:
 *
 * ```text
 * +0x00  class id (0x006A35F4)
 * +0x04  float, +0x08 float
 * +0x0C  0
 * +0x10  the transform: three basis rows and a translation, 16 bytes each (the fourth float of every
 *        row is unused and reads as NaN), then a second set of four vectors that is the same on every
 *        child of a fragment (0x80 bytes together)
 * +0x90  drawable offset   <- the field that says where the piece's geometry is
 * +0x94  0, +0x98 four offsets, +0xA8 four zeros, +0xB8 one offset
 * ```
 *
 * The fields behind the drawable offset are kept as raw words: they are pointers into the resource in
 * the files seen so far, but nothing on the render path reads them, and a word that is not a pointer
 * (0 or -1) must not throw here.
 */
public class FragTypeChild {

    /** where the drawable of this piece sits, relative to the structure */
    static final int DRAWABLE_OFFSET = 0x90;

    /** where the piece's transform sits: four vectors of four floats, see [transform] */
    static final int TRANSFORM_OFFSET = 0x10;

    /** how much of the structure has to be inside the buffer */
    private static final int LENGTH = DRAWABLE_OFFSET + 4;

    public final int startOffset;
    public float unknown1;
    public float unknown2;

    /**
     * The piece's placement in the fragment: basis row 0, row 1, row 2 and the translation, each as
     * `(x, y, z)` - `v' = row0 * v.x + row1 * v.y + row2 * v.z + translation`. Measured on
     * `cj_gb_bench_2.wft`: the rows are the identity and the translation places each slat (child 0 at
     * `-1.14, 0.03, 0.30`, child 6 at `1.02, 0.04, 0.30` - the two ends of the bench). The geometry of a
     * piece is in its own local space around the origin, so without this every piece is drawn where its
     * own vertices sit and a multi-piece fragment is a pile of its parts.
     */
    public final float[] transform = new float[12];

    /** the piece's drawable, 0 when the file does not point at one */
    public int drawableOffset;

    public FragTypeChild(byte[] buffer, int offset) {
        if (offset <= 0 || offset + LENGTH > buffer.length) {
            throw new IllegalStateException("Fragment child at 0x" + Integer.toHexString(offset)
                + " lies outside the resource");
        }
        startOffset = offset;
        unknown1 = Float.intBitsToFloat(wordAt(buffer, offset + 4));
        unknown2 = Float.intBitsToFloat(wordAt(buffer, offset + 8));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                transform[row * 3 + col] = Float.intBitsToFloat(
                    wordAt(buffer, offset + TRANSFORM_OFFSET + row * 16 + col * 4));
            }
        }
        int word = wordAt(buffer, offset + DRAWABLE_OFFSET);
        drawableOffset = (word >>> 28) == 5 ? (word & 0x0fffffff) : 0;
    }

    /** the word at an offset, -1 when it lies outside the buffer; never throws */
    private int wordAt(byte[] buffer, int at) {
        if (at < 0 || at + 4 > buffer.length) {
            return -1;
        }
        return (buffer[at] & 0xff) | ((buffer[at + 1] & 0xff) << 8)
            | ((buffer[at + 2] & 0xff) << 16) | ((buffer[at + 3] & 0xff) << 24);
    }
}
