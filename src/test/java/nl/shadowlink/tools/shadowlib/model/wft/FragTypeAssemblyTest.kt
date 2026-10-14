package nl.shadowlink.tools.shadowlib.model.wft

import nl.shadowlink.tools.shadowlib.model.model.Element
import nl.shadowlink.tools.shadowlib.model.wdr.ModelFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A fragment is one element assembled from many pieces (see `Model.createModelFromFragType`): every piece
 * brings its geometry in the *fragment's* space and each geometry's indices are local to itself.
 *
 * Three things broke multi-piece fragments (measured on `cj_gb_bench_2.wft`, 14 pieces, and over the
 * install's props with `WftPoseProbe`): the strip a geometry went into was the *geometry's* index - which
 * restarts at 0 for every piece while the strip list keeps growing, so every piece behind the first landed
 * in the first piece's strips and the rest stayed empty - and, on top of that, the eight vectors a
 * `fragTypeChild` carries were read as a *placement* and applied to the vertices, which moved whole pieces
 * (median 1.0 m, 51 m at most) and put "parts of the .wft models under the map". Single-piece fragments
 * (`cj_fence_4_1.wft`), `.wdr` models and a *symmetric* assembly (a bench stretched to twice its length
 * still looks like a bench) could not see the last one.
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
    fun `a piece's vertices are read as the file holds them, its own frame is not applied`() {
        // the frame of `cj_gb_bench_2.wft` child 0 (an offset of 1.14 m) must not move the vertex: the
        // geometry of a piece is already in the fragment's space (see FragTypeChild.transform)
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
