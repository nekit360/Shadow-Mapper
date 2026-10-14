package nl.shadowlink.tools.shadowlib.model.wft;

/**
 * One piece of a fragment type: a `fragTypeChild` structure of the resource.
 *
 * Measured on `cj_fence_4_1.wft`: the structure is 0xBC bytes long, starts with a class id and two
 * floats, carries eight vectors, and holds the pointer to the piece's own drawable 0x90 bytes in:
 *
 * ```text
 * +0x00  class id (0x006A35F4)
 * +0x04  float, +0x08 float   (per-piece damage/mass numbers: 50/50, 200/200, 5/5, 100/3.7363)
 * +0x0C  two u16, the same value twice (0000 0000 for the first piece, 00030003, 00040004, ...)
 * +0x10  the frame: three basis rows and an offset, 16 bytes each (the fourth float of every
 *        row is unused and reads as NaN), then a second set of four vectors that is the same on
 *        every child of a fragment (0x80 bytes together)
 * +0x90  drawable offset   <- the field that says where the piece's geometry is
 * +0x94  0, +0x98 four offsets, +0xA8 four zeros, +0xB8 one offset
 * ```
 *
 * The fields behind the drawable offset are kept as raw words: they are pointers into the resource in
 * the files seen so far, but nothing on the render path reads them, and a word that is not a pointer
 * (0 or -1) must not throw here.
 *
 * **The frame is a placement for the pieces that are stored in it.** Both kinds of piece exist in the
 * retail files: `bm_nylamp1`'s pole, its base and its pedestal are stored standing where they belong (the
 * frame's inverse offset, the piece's bone, lies *inside* the piece's own bound), while the lamp arms, the
 * signal heads and the fixtures are stored around the fragment's origin and are placed by the **inverse**
 * of the frame (`v' = R^T (v - t)`), which is the only thing that puts them up the pole where the game
 * draws them. Which is which is decided by `WftPiecePose`; the measurement, the population counts over the
 * install's props and the before/after frames are in `STATUS.md` and `NEXT-STEPS.md` item 13.
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
     * The eight vectors of this piece's own frame: three basis rows, an offset, and a second set of four
     * vectors that is the same on every child of a fragment.
     *
     * **This is not a placement for the piece's geometry.** Measured over the install's props
     * (`WftPoseProbe`, 882 fragments): a piece's geometry and its drawable's declared bounds live in the
     * *fragment's* space already, and the offset moves whole pieces (median 1.0 m, 90th percentile 4.6 m,
     * 51 m at most) - which is what puts "parts of the .wft models under the map". The game's own `.ide`
     * box of a fragment equals the untouched box of its first piece for 763 of 778 models (98.1 %), and
     * 91.1 % of the 617 multi-piece fragments assemble into one connected object without the offset
     * against 38.9 % with it. The vectors are the piece's own frame (its pivot and basis as the exporter
     * wrote them), read here because the format carries them - nothing on the render path may apply them.
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
