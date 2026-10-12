package nl.shadowlink.tools.shadowmapper.render

import nl.shadowlink.tools.io.ByteReader
import nl.shadowlink.tools.io.ReadFunctions
import nl.shadowlink.tools.shadowlib.img.ImgItem
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic
import nl.shadowlink.tools.shadowlib.utils.GameType

/**
 * The texture dictionaries of one image, parsed on demand and kept in a small cache.
 *
 * In GTA: IV a shader only names its texture and the game resolves that name against what is streamed, not
 * against the one TXD of the model's `.ide` entry: a model regularly asks for a name that lives in the
 * dictionary of a neighbouring model. In `nj_01.img` seven of eight such names are in another `.wtd` of the
 * same image (and one of the models' own dictionaries answers only 43 % of their names).
 *
 * Parsing every dictionary of the image up front costs about a second per image (`nj_01.img`: 107
 * dictionaries, 1 088 ms, 86 MB of DXT data) and holding all of them for the image exhausted the heap on a
 * ten-district load, so instead:
 *
 * * a dictionary is parsed when a name needs it, and every name it holds is indexed (`name -> item offset`,
 *   a few thousand strings per image);
 * * only the most recently used dictionaries stay in memory, bounded by [budgetBytes];
 * * a name whose dictionary has been dropped is served by parsing that one dictionary again (~10 ms).
 */
class ImageDictionaryCache(
    private val rf: ReadFunctions?,
    private val dictionaries: List<ImgItem>,
    private val gameType: GameType
) {

    private val byOffset = HashMap<Int, ImgItem>(dictionaries.size * 2)
    private val nameToOffset = HashMap<String, Int>()
    /** access order: the first entry is the least recently used one */
    private val retained = LinkedHashMap<Int, TextureDic>(16, 0.75f, true)
    private var retainedBytes = 0L

    /** how much DXT data may stay in memory at once (one image's worth of dictionaries is ~86 MB) */
    var budgetBytes = 64L * 1024 * 1024

    /** how many dictionaries had to be parsed (for the log) */
    var parsedDictionaries = 0
        private set

    /** how often a name could be served without parsing anything */
    var indexHits = 0
        private set

    private var cursor = 0

    init {
        dictionaries.forEach { byOffset[it.offset] = it }
    }

    /**
     * The dictionary of this image that holds [name].
     *
     * @return the dictionary, or null when this image has no such texture
     */
    fun dictionaryWith(name: String): TextureDic? {
        val key = name.lowercase()
        val known = nameToOffset[key]
        if (known != null) {
            indexHits++
            dictionaryAt(known)?.let { return it }
        }
        while (cursor < dictionaries.size) {
            val item = dictionaries[cursor++]
            val dictionary = parse(item.offset) ?: continue
            if (dictionary.findTexture(name) != null) return dictionary
        }
        return null
    }

    /** The dictionary at that `.img` item offset, parsed if it is not in memory. */
    fun dictionaryAt(offset: Int): TextureDic? = retained[offset] ?: parse(offset)

    /** The dictionary with that item name (".wtd" file), parsed if it is not in memory. */
    fun dictionaryNamed(itemName: String): TextureDic? {
        val item = dictionaries.firstOrNull { it.name.equals(itemName, ignoreCase = true) } ?: return null
        return dictionaryAt(item.offset)
    }

    fun clear() {
        retained.clear()
        nameToOffset.clear()
        retainedBytes = 0
        cursor = 0
    }

    private fun parse(offset: Int): TextureDic? {
        val item = byOffset[offset] ?: return null
        val rf = rf ?: return null
        val dictionary = try {
            rf.seek(item.offset)
            val br: ByteReader = rf.getByteReader(item.size)
            TextureDic(item.name, br, gameType, true, item.size)
        } catch (t: Throwable) {
            // a dictionary that does not parse is simply not offered (12 of 107 in nj_01.img)
            return null
        }
        parsedDictionaries++
        dictionary.textures.forEach { nameToOffset.putIfAbsent(it.diffuseTexName.lowercase(), offset) }
        retained[offset]?.let { old -> retainedBytes -= old.dataSize() }
        retained[offset] = dictionary
        retainedBytes += dictionary.dataSize()
        while (retainedBytes > budgetBytes && retained.size > 1) {
            val oldest = retained.keys.first()
            retained.remove(oldest)?.let { retainedBytes -= it.dataSize() }
        }
        return dictionary
    }

    private fun TextureDic.dataSize(): Long = textures.sumOf { it.data.size.toLong() }
}
