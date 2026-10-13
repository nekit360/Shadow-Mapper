package nl.shadowlink.tools.shadowlib.ide

import nl.shadowlink.tools.shadowlib.utils.Constants
import nl.shadowlink.tools.shadowlib.utils.GameType
import nl.shadowlink.tools.shadowlib.utils.saving.Saveable
import nl.shadowlink.tools.shadowlib.utils.saving.SaveableFile
import java.io.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author Shadow-Link
 */
class IDE(
    var fileName: String,
    var gameType: GameType,
    autoLoad: Boolean
) : Saveable by SaveableFile() {
    private var fileReader: FileReader? = null
    private var input: BufferedReader? = null
    private var readItem = -1 //used to identify what type of section we are reading

    /** Records of [fileName] a reader accepted (a record that threw is counted in [skippedRecords]). */
    var readRecords = 0
        private set

    /** Records of [fileName] no reader could read - one unreadable line must not take the map down. */
    var skippedRecords = 0
        private set

    val itemObjs = mutableListOf<ItemObject>()
    val itemTobj = mutableListOf<ItemTimedObject>()
    val itemTree = mutableListOf<ItemTree>()
    val itemPath = mutableListOf<ItemPath>()
    val itemAnim = mutableListOf<ItemAnimated>()
    val itemTAnm = mutableListOf<ItemTimedAnimated>()
    val itemMlo = mutableListOf<ItemMlo>()
    val item2dfx = mutableListOf<Item2DFX>()
    val itemsAMat = mutableListOf<ItemAnimatedMaterial>()
    val itemTxdp = mutableListOf<ItemTxdPack>()
    val itemCars = mutableListOf<ItemCars>()

    init {
        if (autoLoad) loadIDE()
    }

    private fun loadIDE(): Boolean {
        if (openIDE()) {
            try {
                var line: String? = null
                var lineNumber = 0
                while (input!!.readLine().also { line = it } != null) {
                    lineNumber++
                    if (readItem == -1) {
                        if (line!!.startsWith("#")) {
                            println("Commentaar: $line")
                        } else if (line!!.startsWith("2dfx")) {
                            readItem = Constants.i2DFX
                        } else if (line!!.startsWith("anim")) {
                            readItem = Constants.iANIM
                        } else if (line!!.startsWith("cars")) {
                            readItem = Constants.iCARS
                        } else if (line!!.startsWith("hier")) {
                            readItem = Constants.iHIER
                        } else if (line!!.startsWith("mlo")) {
                            readItem = Constants.iMLO
                        } else if (line!!.startsWith("objs")) {
                            readItem = Constants.iOBJS
                        } else if (line!!.startsWith("path")) {
                            readItem = Constants.iPATH
                        } else if (line!!.startsWith("peds")) {
                            readItem = Constants.iPEDS
                        } else if (line!!.startsWith("tanm")) {
                            readItem = Constants.iTANM
                        } else if (line!!.startsWith("tobj")) {
                            readItem = Constants.iTOBJ
                        } else if (line!!.startsWith("tree")) {
                            readItem = Constants.iTREE
                        } else if (line!!.startsWith("txdp")) {
                            readItem = Constants.iTXDP
                        } else if (line!!.startsWith("weap")) {
                            readItem = Constants.iWEAP
                        }
                        //Message.displayMsgHigh("Started reading item " + readItem);
                    } else {
                        if (line!!.startsWith("end")) {
                            //Message.displayMsgHigh("Item " + readItem + " ended");
                            readItem = -1
                        } else if (line!!.startsWith("#")) {
                            println("Comment: $line")
                        } else if (line!!.isEmpty()) {
                            println("Empty line")
                        } else {
                            readRecord(line!!, lineNumber)
                        }
                    }
                }
                closeIDE()
                if (skippedRecords > 0) {
                    println(
                        "Warning: $fileName: $skippedRecords of ${readRecords + skippedRecords} records " +
                                "could not be read"
                    )
                }
            } catch (ex: IOException) {
                Logger.getLogger(IDE::class.java.name).log(Level.SEVERE, null, ex)
            }
        } else {
            //Message.displayMsgHigh("Unable to open file: " + fileName);
        }
        return true
    }

    /**
     * Reads one record and stores it. A reader that throws on a shape it does not know used to take the
     * whole load with it: on the modded install one `OBJS` row with a column missing died inside
     * `ItemObject.read`, the exception crossed the constructor of this class and the load ended with 0
     * IPLs and 0 images - an empty map. That one record is skipped and named with its file and line
     * instead, and the rest of the map loads.
     *
     * A record is stored only after it has been read, because the readers fill `lateinit` fields: an
     * item that failed halfway through would throw later, from somewhere else entirely.
     */
    private fun readRecord(line: String, lineNumber: Int) {
        val item = when (readItem) {
            Constants.i2DFX -> Item2DFX(gameType)
            Constants.iANIM -> ItemAnimated(gameType)
            Constants.iCARS -> ItemCars(gameType)
            Constants.iHIER -> ItemHier(gameType)
            Constants.iMLO -> ItemMlo(gameType)
            Constants.iOBJS -> ItemObject(gameType)
            Constants.iPATH -> ItemPath(gameType)
            Constants.iPEDS -> ItemPeds(gameType)
            Constants.iTANM -> ItemTimedAnimated(gameType)
            Constants.iTOBJ -> ItemTimedObject(gameType)
            Constants.iTREE -> ItemTree(gameType)
            Constants.iTXDP -> ItemTxdPack(gameType)
            Constants.iWEAP -> ItemWeapon(gameType)
            else -> return
        }
        try {
            item.read(line)
        } catch (ex: Exception) {
            skippedRecords++
            println(
                "Warning: $fileName line $lineNumber: skipped a record " +
                        "(${ex.javaClass.simpleName}: ${ex.message}): $line"
            )
            return
        }
        readRecords++
        when (item) {
            // The subclasses first: an ItemAnimated or an ItemTimedObject IS an ItemObject, and its
            // record belongs in its own list (and back into its own section on save), not in itemObjs.
            is ItemAnimated -> itemAnim.add(item)
            is ItemTimedObject -> itemTobj.add(item)
            is ItemCars -> itemCars.add(item)
            is ItemTxdPack -> itemTxdp.add(item)
            is ItemObject -> {
                itemObjs.add(item)
                if (item.relaxedRead) {
                    println(
                        "Warning: $fileName line $lineNumber: ${item.modelName}: the record carries no " +
                                "second flags column, its bounds were read one column to the left: $line"
                    )
                }
            }

            else -> {} // sections whose reader keeps nothing but the name are not stored anywhere
        }
    }

    /**
     * The object a model name belongs to, out of every section that declares objects.
     *
     * `objs` is where the bulk of the map's models live, but the format declares objects in two more
     * sections and a WPL places them as ordinary instances: `tobj` (the timed objects - the district's
     * night windows and emissive LODs, 2 752 rows in this install) and `anim` (the section's own objects,
     * 160 rows here). Looking in `objs` alone left 1 754 instances of a clean install with no model at
     * all - the renderer draws nothing for such an instance (`STATUS.md`, "Models the editor could not
     * find in the IDE").
     *
     * The lists are small and the renderer asks once per instance per loaded model, so this stays a scan.
     */
    fun findItem(name: String): ItemObject? {
        findIn(itemObjs, name)?.let { return it }
        findIn(itemTobj, name)?.let { return it }
        return findIn(itemAnim, name)
    }

    private fun <T : ItemObject> findIn(items: List<T>, name: String): T? =
        items.firstOrNull { it.modelName == name }

    fun openIDE(): Boolean {
        try {
            fileReader = FileReader(fileName)
            input = BufferedReader(fileReader)
        } catch (ex: IOException) {
            return false
        }
        return true
    }

    fun closeIDE(): Boolean {
        try {
            input!!.close()
            fileReader!!.close()
        } catch (ex: IOException) {
            return false
        }
        return true
    }

    fun save() {
        try {
            var fileWriter: FileWriter? = null
            var output: BufferedWriter? = null
            fileWriter = FileWriter(fileName)
            output = BufferedWriter(fileWriter)
            output.write("objs")
            output.newLine()
            itemObjs.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("tobj")
            output.newLine()
            itemTobj.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("tree")
            output.newLine()
            itemTree.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("path")
            output.newLine()
            itemPath.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("anim")
            output.newLine()
            itemAnim.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("tanm")
            output.newLine()
            itemTAnm.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("mlo")
            output.newLine()
            itemMlo.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("2dfx")
            output.newLine()
            item2dfx.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("amat")
            output.newLine()
            itemsAMat.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.write("txdp")
            output.newLine()
            itemTxdp.forEach { it.save(output) }
            output.write("end")
            output.newLine()
            output.flush()
            output.close()
            fileWriter.close()
        } catch (ex: IOException) {
            Logger.getLogger(IDE::class.java.name).log(Level.SEVERE, null, ex)
        }
    }
}