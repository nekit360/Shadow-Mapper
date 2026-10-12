package nl.shadowlink.tools.shadowmapper.render

import nl.shadowlink.tools.io.ReadFunctions
import nl.shadowlink.tools.shadowlib.img.Img
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic
import nl.shadowlink.tools.shadowlib.utils.GameType
import nl.shadowlink.tools.shadowmapper.FileManager

/**
 * The dictionaries of the images *around* the one being loaded.
 *
 * A model of `nj_01.img` (Broker) names textures that live in `nj_02.img`: `NJ01_FreewayBase` is in
 * `nj02_freeway.wtd`, and `NJ01_girder` is in none of the parseable dictionaries of `nj_01.img` at all.
 * The game streams the neighbouring districts and resolves a name against everything that is streamed; the
 * editor loads the images of the ticked IPLs, so the images of the same name group are searched here, one
 * at a time and on demand, only for a name that neither the model nor its own image could answer.
 *
 * A searched image keeps its name index (a few thousand strings) and an [ImageDictionaryCache] with a small
 * budget, so a second name of the same image is answered without parsing anything again; the number of
 * images that may be indexed is capped, because a name that is nowhere would otherwise walk the whole map.
 *
 * @param fm the images of the game, in the order they were scanned
 */
class NeighbourDictionaries(
    private val fm: FileManager,
    private val gameType: GameType
) {

    private val caches = HashMap<String, ImageDictionaryCache>()
    private val scanners = HashMap<String, ReadFunctions>()

    /** how many further images may be indexed for this load */
    var maxImages = 12

    /** how much DXT data one searched image may keep (it is only needed to answer a name) */
    var budgetBytes = 16L * 1024 * 1024

    /** the images that were actually searched, for the log */
    val searched = ArrayList<String>()

    /**
     * The dictionary of a neighbouring image that holds [name].
     *
     * @param current the image being loaded - not searched, its own index is consulted first
     * @return the dictionary, or null when no image of the group has that texture
     */
    fun dictionaryWith(name: String, current: String): TextureDic? {
        val group = groupOf(current)
        if (group.isEmpty()) return null
        for (img in fm.imgs) {
            if (img.fileName == current || groupOf(img.fileName) != group) continue
            val cache = caches[img.fileName]
            if (cache == null && caches.size >= maxImages) continue
            val dictionary = cacheOrCreate(img).dictionaryWith(name)
            if (dictionary != null) return dictionary
        }
        return null
    }

    /** Releases every searched image: their dictionaries, their indexes and their open files. */
    fun clear() {
        caches.values.forEach { it.clear() }
        caches.clear()
        scanners.values.forEach { it.closeFile() }
        scanners.clear()
        searched.clear()
    }

    private fun cacheOrCreate(img: Img): ImageDictionaryCache {
        caches[img.fileName]?.let { return it }
        val rf: ReadFunctions = try {
            ReadFunctions(img.path)
        } catch (t: Throwable) {
            // an image that cannot be opened simply offers nothing
            return ImageDictionaryCache(null, emptyList(), gameType)
        }
        scanners[img.fileName] = rf
        searched.add(img.fileName)
        val cache = ImageDictionaryCache(rf, img.getItemsOfType(".wtd"), gameType)
        cache.budgetBytes = budgetBytes
        caches[img.fileName] = cache
        return cache
    }

    companion object {
        /**
         * The name group of an image: `nj_01.img` and `nj_liberty.img` are both "nj", `manhat01.img` is
         * "manhat". A name that starts with something that is not a letter has no group (and no neighbours),
         * so that an odd file name cannot make every image a candidate.
         */
        fun groupOf(fileName: String): String =
            fileName.substringBeforeLast('.').takeWhile { it.isLetter() }
    }
}
