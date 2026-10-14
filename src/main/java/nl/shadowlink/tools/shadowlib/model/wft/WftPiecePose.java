package nl.shadowlink.tools.shadowlib.model.wft;

import nl.shadowlink.tools.shadowlib.model.wdr.DrawableModel;

/**
 * Which pieces of a fragment have to be placed by their own frame, and which are read as the file holds
 * them.
 *
 * A `fragTypeChild` carries eight vectors at +0x10: three basis rows and an offset, twice
 * ({@link FragTypeChild#transform}). That frame maps the *fragment's* space into the piece's own, so:
 *
 * * a piece whose vertices are stored **in that frame** is placed by its **inverse** - `v' = R^T (v - t)`,
 *   which is what {@code ModelFile.addVertices} does when it is handed a frame;
 * * a piece whose vertices are stored in the **fragment's** space is read unchanged.
 *
 * **Both kinds exist in the retail files, and the frame itself says which is which.** Measured over the
 * install's props (`WftPoseProbe`, 5 269 pieces of 863 fragments): the pieces that are stored in their frame
 * are the ones whose geometry sits around the fragment's origin - the lamp arms, the signal heads, the
 * fixtures, the slats of a bench - and for those the frame's offset is the negative of a point that lies
 * **outside** the piece's own stored bound (the piece's bone, i.e. where the piece belongs, is elsewhere).
 * The pieces that are already in place are the poles, bases and posts: their stored geometry spans from the
 * ground up, and the frame's inverse offset lands **inside** that geometry (the bone sits in the mesh it
 * drives). Testing the two against each other is what this class does.
 *
 * The alternative - applying a frame to everything - is the attempt of commit `e9556b` (which *added* the
 * offset and sent whole pieces under the map) and of its inverse (which lifts the poles out of their bases);
 * `-Dsm.wft.pose=inverse` still reproduces it for an A/B frame. The measurement and the before/after frames
 * are in `STATUS.md` and `NEXT-STEPS.md` item 13.
 */
public final class WftPiecePose {

    /** the behaviour before this round: every piece read exactly as the file holds it */
    public static final String RAW = "raw";

    /** every piece placed by its own frame (commit `e9556b` with the sign corrected) */
    public static final String INVERSE = "inverse";

    /** the default: only the pieces whose bone lies outside their own stored bound */
    public static final String RULE = "rule";

    /** the mode the renderer uses, `-Dsm.wft.pose=raw|inverse|rule` */
    public static String mode() {
        return System.getProperty("sm.wft.pose", RULE);
    }

    /**
     * The frame a piece has to be placed by, or null when its vertices are read as the file holds them.
     *
     * @param mode  one of {@link #RAW}, {@link #INVERSE}, {@link #RULE}
     * @param frame the piece's `fragTypeChild`, null when the file carried none
     * @param piece the piece's drawable, whose declared bound is the geometry's own extent
     */
    public static float[] poseFor(String mode, FragTypeChild frame, DrawableModel piece) {
        if (frame == null || piece == null || RAW.equals(mode)) {
            return null;
        }
        if (INVERSE.equals(mode)) {
            return frame.transform;
        }
        // the frame's offset is the *inverse* translation: the piece's bone sits at -t
        float boneX = -frame.transform[9];
        float boneY = -frame.transform[10];
        float boneZ = -frame.transform[11];
        boolean storedInPlace = boneX >= piece.BoundsMin.x && boneX <= piece.BoundsMax.x
            && boneY >= piece.BoundsMin.y && boneY <= piece.BoundsMax.y
            && boneZ >= piece.BoundsMin.z && boneZ <= piece.BoundsMax.z;
        return storedInPlace ? null : frame.transform;
    }

    private WftPiecePose() {
    }
}
