package nl.shadowlink.tools.shadowlib.model.wft

import nl.shadowlink.tools.io.Vector4D
import nl.shadowlink.tools.shadowlib.model.model.Element
import nl.shadowlink.tools.shadowlib.model.wdr.DrawableModel
import nl.shadowlink.tools.shadowlib.model.wdr.ModelFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A fragment is one element assembled from many pieces (see `Model.createModelFromFragType`): every piece
 * brings its geometry and each geometry's indices are local to itself, and the piece is placed by its own
 * frame only when the frame says the geometry is stored in it (`WftPiecePose`).
 *
 * What broke multi-piece fragments, in the order it was found: the strip a geometry went into was the
 * *geometry's* index - which restarts at 0 for every piece while the strip list keeps growing, so every
 * piece behind the first landed in the first piece's strips and the rest stayed empty (`cj_gb_bench_2`, 14
 * pieces, 27 of 30 strips empty); then the eight vectors at +0x10 of a `fragTypeChild` were read as a
 * placement and *added* to the vertices, which moved whole pieces the wrong way (median 1.0 m, 51 m at
 * most) and put "parts of the .wft models under the map"; then the same vectors were dropped altogether,
 * which left every arm, head and fixture of these files sitting at the fragment's origin (`bm_nylamp1`'s
 * lamp arm and `bm_nytraflite6d`'s mast arm at the foot of the pole). The rule that ends it is measured over
 * the install's props with `WftPoseProbe` and lives in `WftPiecePose`.
 */
internal class FragTypeAssemblyTest {

    /** a fragTypeChild structure: the four transform vectors at +0x10, the drawable pointer at +0x90 */
    private fun child(transformRows: FloatArray, drawable: Int): FragTypeChild {
        // the reader refuses offset 0 (a real child is never at the start of the resource), so the
        // structure is placed behind a pad
        val base = 0x10
        val buffer = ByteArray(base + 0xBC)
        fun put(offset: Int, value: Int) {
            buffer[base + offset] = (value and 0xff).toByte()
            buffer[base + offset + 1] = ((value ushr 8) and 0xff).toByte()
            buffer[base + offset + 2] = ((value ushr 16) and 0xff).toByte()
            buffer[base + offset + 3] = ((value ushr 24) and 0xff).toByte()
        }
        for (row in 0 until 4) {
            for (col in 0 until 3) {
                put(0x10 + row * 16 + col * 4, transformRows[row * 3 + col].toRawBits())
            }
        }
        put(0x90, (5 shl 28) or (drawable and 0x0fffffff))
        return FragTypeChild(buffer, base)
    }

    @Test
    fun `a child carries its own frame - three basis rows and an offset`() {
        // cj_gb_bench_2.wft child 0 as the file writes it: identity rows, the slat's own offset
        val transform = child(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
                -1.138645f, 0.031463f, 0.299818f,
            ),
            drawable = 0xa3c0,
        ).transform

        assertEquals(1f, transform[0], 1e-6f)
        assertEquals(0f, transform[1], 1e-6f)
        assertEquals(-1.138645f, transform[9], 1e-6f)
        assertEquals(0.031463f, transform[10], 1e-6f)
        assertEquals(0.299818f, transform[11], 1e-6f)
    }

    @Test
    fun `a rotated child keeps its basis rows and its drawable pointer`() {
        val piece = child(
            floatArrayOf(
                0f, -1f, 0f,
                1f, 0f, 0f,
                0f, 0f, 1f,
                3f, 4f, 5f,
            ),
            drawable = 0x1234,
        )
        assertEquals(0f, piece.transform[0], 1e-6f)
        assertEquals(-1f, piece.transform[1], 1e-6f)
        assertEquals(1f, piece.transform[3], 1e-6f)
        assertEquals(0f, piece.transform[4], 1e-6f)
        assertEquals(0x1234, piece.drawableOffset)
    }

    @Test
    fun `a reader without a frame reads the vertices as the file holds them`() {
        // the raw path (`WftPiecePose.RAW`, and every `.wdr`): the caller hands no frame, so the vertex is
        // taken as stored. The choice per piece is `WftPiecePose.poseFor`, tested below.
        val stream = ByteArray(36) // one vertex, 36-byte record, position at byte 0
        ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN).putFloat(0, 1f).putFloat(4, 2f).putFloat(8, 3f)
        val element = Element()
        element.createStrip(1, 0)
        ModelFile().addVertices(element, stream, 0, 36, 1, 0, 36, 0)

        val vertex = element.getStrip(0).getVertex(0)
        assertEquals(1f, vertex.x, 1e-6f)
        assertEquals(2f, vertex.y, 1e-6f)
        assertEquals(3f, vertex.z, 1e-6f)
    }

    @Test
    fun `a piece whose bone lies outside its stored bound is placed by its frame`() {
        // bm_nytraflite6d.wft's mast arm as the file writes it: stored around the fragment's origin (x
        // -4.41..0.10, z -0.72..0.86) with the frame (identity rows) offset (0.0589, 0.0563, -5.0008). Its
        // bone at -t = (-0.06, -0.06, 5.00) lies outside that bound, i.e. the geometry is stored in the
        // frame and belongs 5 m up the pole - where the game draws the arm.
        val arm = child(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
                0.058928f, 0.056286f, -5.000785f,
            ),
            drawable = 0x3f60,
        )
        val piece = drawable(-4.405054f, -0.015455f, -0.719164f, 0.101206f, 0.061579f, 0.856860f)
        val pose = WftPiecePose.poseFor(WftPiecePose.RULE, arm, piece)
        assertNotNull(pose)

        // and the reader places the vertex by the inverse of that frame: v' = v - t
        val stream = ByteArray(36)
        ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN).putFloat(0, 0f).putFloat(4, 0f).putFloat(8, 0f)
        val element = Element()
        element.createStrip(1, 0)
        ModelFile().addVertices(element, stream, 0, 36, 1, 0, 36, 0, pose)

        val vertex = element.getStrip(0).getVertex(0)
        assertEquals(-0.058928f, vertex.x, 1e-5f)
        assertEquals(-0.056286f, vertex.y, 1e-5f)
        assertEquals(5.000785f, vertex.z, 1e-5f)
    }

    @Test
    fun `a pole whose bone lies inside its stored bound is read as the file holds it`() {
        // bm_nylamp1.wft's pole: stored standing on the ground (-0.27 m .. 6.25 m) with the frame offset
        // (0.0133, 0.0343, -2.1950), i.e. its bone sits 2.2 m up the pole, inside the geometry - the piece
        // is already in the fragment's space and moving it would lift the lamp off its base.
        val pole = child(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
                0.013345f, 0.034320f, -2.195041f,
            ),
            drawable = 0x4fa0,
        )
        val piece = drawable(-0.156197f, -0.147804f, -0.270444f, 0.139413f, 0.147805f, 6.247068f)

        assertNull(WftPiecePose.poseFor(WftPiecePose.RULE, pole, piece))
        assertNull(WftPiecePose.poseFor(WftPiecePose.RAW, pole, piece))
        assertNotNull(WftPiecePose.poseFor(WftPiecePose.INVERSE, pole, piece))
    }

    @Test
    fun `the inverse of a rotated frame is applied as its transpose`() {
        // a quarter turn about z with the offset (3, 4, 5): v' = R^T (v - t), so a vertex at the offset
        // lands on the origin and one 1 m to the side lands 1 m down
        val rotated = child(
            floatArrayOf(
                0f, -1f, 0f,
                1f, 0f, 0f,
                0f, 0f, 1f,
                3f, 4f, 5f,
            ),
            drawable = 0x10,
        )
        val piece = drawable(0f, 0f, 0f, 1f, 1f, 1f)
        val pose = WftPiecePose.poseFor(WftPiecePose.INVERSE, rotated, piece)

        val stream = ByteArray(36)
        ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN).putFloat(0, 4f).putFloat(4, 3f).putFloat(8, 5f)
        val element = Element()
        element.createStrip(1, 0)
        ModelFile().addVertices(element, stream, 0, 36, 1, 0, 36, 0, pose)

        val vertex = element.getStrip(0).getVertex(0)
        assertEquals(-1f, vertex.x, 1e-5f)
        assertEquals(-1f, vertex.y, 1e-5f)
        assertEquals(0f, vertex.z, 1e-5f)
    }

    /** a piece's drawable, of which the rule only needs the declared bound */
    private fun drawable(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): DrawableModel {
        val drawable = DrawableModel()
        drawable.BoundsMin = Vector4D(minX, minY, minZ)
        drawable.BoundsMax = Vector4D(maxX, maxY, maxZ)
        return drawable
    }

    @Test
    fun `each geometry keeps its own strip when pieces are appended`() {
        // the loader creates one strip per geometry and the strip index is the one just created; the
        // geometry index would restart at 0 for the second piece and route it into strip 0 again
        val element = Element()
        val mf = ModelFile()
        val stream = ByteArray(36 * 4)
        val bb = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 4) {
            bb.putFloat(i * 36, i.toFloat())
        }

        // geometry A of piece 0: two vertices at 0 and 36 -> strip 0
        element.createStrip(1, 0)
        var strip = element.getStripCount() - 1
        mf.addVertices(element, stream, 0, 36 * 2, 2, 1, 36, strip)
        mf.addPolygons(element, stream, 0, 36 * 2, 2, 1, 36, strip)
        // geometry A of piece 1: two vertices at 72 and 108, its own indices 0..1 -> strip 1
        element.createStrip(1, 1)
        strip = element.getStripCount() - 1
        mf.addVertices(element, stream, 36 * 2, 36 * 3, 2, 1, 36, strip)
        mf.addPolygons(element, stream, 36 * 2, 36 * 3, 2, 1, 36, strip)

        assertEquals(2, element.getStripCount())
        assertEquals(2, element.getStrip(0).getVertexCount())
        assertEquals(2, element.getStrip(1).getVertexCount())
        // strip 1 holds the second piece's own vertices (x = 2, 3), not the first piece's (0, 1)
        assertEquals(2f, element.getStrip(1).getVertex(0).x, 1e-6f)
        assertEquals(3f, element.getStrip(1).getVertex(1).x, 1e-6f)
    }
}
