package nl.shadowlink.tools.shadowlib.texturedic

/**
 * What the alpha channel of a texture says, decoded straight from its DXT blocks.
 *
 * A texture with an alpha channel is not automatically a transparent surface: the same channel carries a
 * cut-out mask (a leaf, a fence), a **decal** that is painted onto the surface it lies on, an **alpha** preset
 * that is blended by design, or the frosted transparency of a glass pane. What the renderer needs to know is
 * only whether the channel is *used* - if 90 % of the texels are opaque, blending this material is what makes
 * it look right; if all of them are opaque, blending it changes nothing at all.
 *
 * @author Shadow-Link
 */
class AlphaStats(
    /** how many texels were looked at (a big texture is sampled with a stride) */
    val sampled: Int,
    /** the mean alpha of those texels, 0..1 */
    val mean: Float,
    /** the share of them below 0.5, 0..1 - the texels a blend would let the background through */
    val belowHalf: Float,
) {

    /**
     * True when the alpha channel of this texture is used as transparency rather than as a mask.
     *
     * The threshold is measured, not guessed: the textures of the map's glass and of its `*_alpha` materials
     * sit at 32 % (`td_glass_doors`), 33 % (`bx_glass`), 41 % (`NJ01_GLASS`), 69-75 % (the neon and window
     * maps) and 97 % (the mod's dirty glass) of their texels below 0.5, while a texture that only carries an
     * alpha channel for other reasons - the cut-outs (51-82 %) and the decals (22-98 %) - is caught by its
     * preset, not here. An opaque diffuse map measures 0 %.
     */
    val carriesTransparency: Boolean
        get() = belowHalf >= TRANSPARENT_SHARE

    companion object {
        /** the share of texels below 0.5 alpha from which a texture counts as transparent */
        const val TRANSPARENT_SHARE = 0.10f

        /** what is assumed when the alpha channel cannot be decoded: a solid, opaque surface */
        @JvmField
        val OPAQUE = AlphaStats(0, 1f, 0f)
    }
}

/**
 * The most texels one texture is decoded for. A 2048x2048 dictionary texture holds 16.7 M texels, and the mean
 * of every 64th block is the same fact at a cost the load does not feel.
 */
private const val ALPHA_SAMPLE_TEXELS = 65536

/**
 * Decodes the alpha channel of a DXT1/DXT3/DXT5 texture and counts what it holds.
 *
 * Only the level-0 blocks are read (a texture's data may carry its mip chain behind them), and a texture the
 * renderer cannot upload is answered as opaque instead of throwing: this runs while a map is being loaded, and
 * a texture that is odd must cost a warning somewhere else, never the load.
 *
 * @return the statistics of the alpha channel, [AlphaStats.OPAQUE] when there is none to decode
 */
fun Texture.decodeAlphaStats(): AlphaStats {
    val blockSize = when (dxtCompressionType?.uppercase()) {
        "DXT1" -> 8
        "DXT3", "DXT5" -> 16
        else -> return AlphaStats.OPAQUE
    }
    if (width <= 0 || height <= 0 || data.size < blockSize) {
        return AlphaStats.OPAQUE
    }
    val blocksX = (width + 3) / 4
    val blocksY = (height + 3) / 4
    val blocks = blocksX * blocksY
    var stride = 1
    while (blocks / stride > ALPHA_SAMPLE_TEXELS / 16) {
        stride++
    }
    val alphas = IntArray(16)
    var sum = 0L
    var below = 0
    var sampled = 0
    var block = 0
    while (block < blocks) {
        val at = block * blockSize
        if (at + blockSize > data.size) {
            break
        }
        when (dxtCompressionType?.uppercase()) {
            "DXT5" -> dxt5Alpha(data, at, alphas)
            "DXT3" -> dxt3Alpha(data, at, alphas)
            else -> dxt1Alpha(data, at, alphas)
        }
        for (i in 0 until 16) {
            sum += alphas[i]
            if (alphas[i] < 128) {
                below++
            }
        }
        sampled += 16
        block += stride
    }
    if (sampled == 0) {
        return AlphaStats.OPAQUE
    }
    return AlphaStats(sampled, sum.toFloat() / (sampled * 255f), below.toFloat() / sampled)
}

/** The 16 texel alphas of one DXT5 block: two 8-bit endpoints and 16 three-bit indices, interpolated. */
private fun dxt5Alpha(data: ByteArray, at: Int, out: IntArray) {
    val a0 = data[at].toInt() and 0xff
    val a1 = data[at + 1].toInt() and 0xff
    val table = IntArray(8)
    table[0] = a0
    table[1] = a1
    if (a0 > a1) {
        for (i in 1..6) {
            table[i + 1] = ((7 - i) * a0 + i * a1) / 7
        }
    } else {
        table[2] = (4 * a0 + a1) / 5
        table[3] = (3 * a0 + 2 * a1) / 5
        table[4] = (2 * a0 + 3 * a1) / 5
        table[5] = (a0 + 4 * a1) / 5
        table[6] = 0
        table[7] = 255
    }
    var bits = 0L
    for (i in 0 until 6) {
        bits = bits or ((data[at + 2 + i].toLong() and 0xff) shl (8 * i))
    }
    for (i in 0 until 16) {
        out[i] = table[((bits shr (3 * i)) and 7L).toInt()]
    }
}

/** The 16 texel alphas of one DXT3 block: four bits each, expanded to the full range. */
private fun dxt3Alpha(data: ByteArray, at: Int, out: IntArray) {
    for (i in 0 until 16) {
        val packed = data[at + i / 2].toInt() and 0xff
        val nibble = if (i % 2 == 0) packed and 0x0f else (packed shr 4) and 0x0f
        out[i] = nibble * 17
    }
}

/**
 * The 16 texel alphas of one DXT1 block: 255 everywhere, except in the 1-bit-alpha mode (`color0 <= color1`),
 * where the fourth colour index means "no texel at all".
 */
private fun dxt1Alpha(data: ByteArray, at: Int, out: IntArray) {
    val color0 = (data[at].toInt() and 0xff) or ((data[at + 1].toInt() and 0xff) shl 8)
    val color1 = (data[at + 2].toInt() and 0xff) or ((data[at + 3].toInt() and 0xff) shl 8)
    if (color0 > color1) {
        java.util.Arrays.fill(out, 255)
        return
    }
    var bits = 0L
    for (i in 0 until 4) {
        bits = bits or ((data[at + 4 + i].toLong() and 0xff) shl (8 * i))
    }
    for (i in 0 until 16) {
        out[i] = if (((bits shr (2 * i)) and 3L).toInt() == 3) 0 else 255
    }
}
