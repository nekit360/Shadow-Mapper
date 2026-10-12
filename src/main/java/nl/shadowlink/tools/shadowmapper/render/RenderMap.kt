package nl.shadowlink.tools.shadowmapper.render

import com.jogamp.opengl.GL2
import nl.shadowlink.tools.io.ByteReader
import nl.shadowlink.tools.io.ReadFunctions
import nl.shadowlink.tools.io.Vector3D
import nl.shadowlink.tools.shadowlib.ide.ItemObject
import nl.shadowlink.tools.shadowlib.img.ImgItem
import nl.shadowlink.tools.shadowlib.ipl.Item_INST
import nl.shadowlink.tools.shadowlib.model.model.Model
import nl.shadowlink.tools.shadowlib.texturedic.TextureDic
import nl.shadowlink.tools.shadowlib.utils.GameType
import nl.shadowlink.tools.shadowmapper.FileManager
import nl.shadowlink.tools.shadowmapper.gui.PickingType
import nl.shadowlink.tools.shadowmapper.utils.GlTextureCache
import nl.shadowlink.tools.shadowmapper.utils.GlUtil.drawCube
import java.util.*
import kotlin.math.sqrt

/**
 * @author Shadow-Link
 */
class RenderMap(
    private val gameType: GameType,
    private val camera: Camera,
    private val fm: FileManager,
) {

    private var glDisplayList: IntArray = IntArray(0)

    /** GL textures of the loaded map; rebuilt on every load so a reload cannot leak them. */
    private var textureCache: GlTextureCache? = null

    /** The textures of the last load, for the probes and the console line at the end of loadMap. */
    val textures: GlTextureCache?
        get() = textureCache
    var reload = false
    var loading = false
    var added = false
    /** Draw every instance of the selected IPLs, ignoring the draw distance from the IDE. */
    var fullDrawDistance = false
    /** Progress of loadMap, called from the loading thread: (done, total). */
    var progressListener: ((Int, Int) -> Unit)? = null
    private var progressDone = 0
    private var progressTotal = 0
    var tempIDE: ItemObject? = null
    var tempIPL: Item_INST? = null

    fun display(gl: GL2) {
        if (!loading) {
            if (reload) {
                loading = true
                println("Started loading")
                loadMap(gl)
                reload = false
                loading = false
                println("Loading finished")
            }
            if (added) {
                loading = true
                println("Loading added model")
                loadAddedModel(gl)
                loading = false
                added = false
                println("Loading added model finished")
            }
            gl.glPushName(PickingType.map)
            fm.ipls.forEachIndexed { iplIndex, ipl ->
                if (ipl.selected && ipl.itemsLoaded) {
                    gl.glPushName(iplIndex)
                    ipl.itemsInst.forEachIndexed { instanceIndex, item ->
                        if (!item.name.lowercase(Locale.getDefault()).contains("lod")) {
                            if (fullDrawDistance || getDistance(camera.pos, item.position) < item.drawDistance) {
                                gl.glPushName(instanceIndex)
                                if (item.selected) {
                                    gl.glColor3f(0.9f, 0f, 0f)
                                } else {
                                    gl.glColor3f(1f, 1f, 1f)
                                }
                                gl.glPushMatrix()
                                gl.glTranslatef(item.position.x, item.position.y, item.position.z)
                                gl.glRotatef(item.axisAngle.w, item.axisAngle.x, item.axisAngle.y, item.axisAngle.z)
                                gl.glCallList(glDisplayList[item.glListID])
                                gl.glPopMatrix()
                                gl.glPopName()
                            }
                        }
                    }
                    gl.glPopName()
                }
            }
            gl.glPopName()
        }
        gl.glFlush()
    }

    private fun loadMap(gl: GL2) {
        // Clear existing display list
        glDisplayList.forEach { dl -> gl.glDeleteLists(dl, 1) }
        // and the textures of the previous load: every load builds new dictionaries
        textureCache?.dispose()
        val glTextures = GlTextureCache(gl)
        textureCache = glTextures

        // progress is reported on a fixed 0..1000 scale: phase 1 (resolving the instances of the
        // selected IPLs against the IDEs) gets the first 10%, phase 2 (looking for every model in
        // every image) the rest, so the bar never jumps backwards when the phases change
        progressTotal = 1000
        progressDone = 0
        progressListener?.invoke(0, progressTotal)
        val instancesTotal = fm.ipls.sumOf { if (it.selected) it.itemsInst.size else 0 }.coerceAtLeast(1)
        var instancesSeen = 0

        val boolList: ArrayList<Boolean> = ArrayList<Boolean>()
        val ideList: ArrayList<ItemObject> = ArrayList<ItemObject>()
        fm.ipls.forEach { ipl ->
            if (ipl.selected) {
                ipl.itemsInst.forEach { instance ->
                    instancesSeen++
                    if (instancesSeen % 200 == 0) {
                        progressDone = instancesSeen * 100 / instancesTotal
                        progressListener?.invoke(progressDone, progressTotal)
                    }
                    var ideNumber = 0
                    var ideItem = fm.ides[ideNumber].findItem(instance.name) as? ItemObject
                    while (ideItem == null) {
                        ideNumber++
                        ideItem = if (ideNumber < fm.ides.size) {
                            fm.ides[ideNumber].findItem(instance.name) as? ItemObject
                        } else {
                            println("I really can't find in IDE: ${instance.name}")
                            break
                        }
                    }
                    if (ideItem != null) {
                        var found = false
                        for (i in ideList.indices) {
                            if (ideList[i] == ideItem) {
                                found = true
                                instance.glListID = i + 1
                                instance.drawDistance = ideItem.drawDistance[0]
                            }
                        }
                        if (!found) {
                            instance.glListID = ideList.size + 1
                            instance.drawDistance = ideItem.drawDistance[0]
                            ideList.add(ideItem)
                            boolList.add(false)
                        }
                    } else {
                        instance.glListID = 0
                    }
                }

                ipl.itemsLoaded = true
            }
        }
        glDisplayList = IntArray(ideList.size + 1)
        println("ideList Size: " + ideList.size)
        // the rest of the work is scanning every image for every model they may contain
        val phase2Total = (fm.imgs.size * ideList.size).coerceAtLeast(1)
        var phase2Seen = 0
        progressDone = 100
        progressListener?.invoke(progressDone, progressTotal)
        for (imgNumber in fm.imgs.indices) {
            val rf = ReadFunctions(fm.imgs[imgNumber].path)
            println("Opened: " + fm.imgs[imgNumber].fileName)
            // In GTA: IV a shader only names its texture and the game resolves that name against what is
            // streamed, so a model asks for names that live in the dictionary of a neighbouring model
            // (in NJ_01 the model's own two dictionaries answer 69 % of the names, the rest are in other
            // .wtd of the same image). ImageDictionaryCache offers the dictionaries of this image by name
            // and parses them on demand; it keeps only a bounded set of them, so the memory does not grow
            // with the size of the image.
            val imageDictionaries =
                ImageDictionaryCache(rf, fm.imgs[imgNumber].getItemsOfType(".wtd"), gameType)
            glTextures.onNameMissing = { name -> imageDictionaries.dictionaryWith(name) }
            for (i in ideList.indices) {
                phase2Seen++
                if (phase2Seen % 200 == 0) {
                    progressDone = 100 + phase2Seen * 900 / phase2Total
                    progressListener?.invoke(progressDone, progressTotal)
                }
                if (!boolList[i]) {
                    var modelName = ""
                    var item: ImgItem? = null
                    if (ideList[i].WDD != "null") {
                        modelName = ideList[i].WDD + ".wdd"
                        item = fm.imgs[imgNumber].findItem(modelName)
                    } else {
                        modelName = ideList[i].modelName + ".wdr"
                        item = fm.imgs[imgNumber].findItem(modelName)
                        if (item == null) item = fm.imgs[imgNumber].findItem(ideList[i].modelName + ".wft")
                    }
                    if (item != null) {
                        val itemName = item.name
                        var mdl: Model? = null
                        // parsing must not take the whole render loop down: one broken
                        // .wdr/.wft/.wdd used to abort loadMap() and freeze the 3D view
                        try {
                            rf.seek(item.offset)
                            val br = rf.getByteReader(item.size)
                            if (itemName.endsWith(".wdr", ignoreCase = true)) {
                                println(itemName)
                                mdl = Model().loadWDR(br, item.size)
                            } else if (itemName.endsWith(".wdd", ignoreCase = true)) {
                                mdl = Model().loadWDD(br, item.size, ideList[i].modelName)
                            } else if (itemName.endsWith(".wft", ignoreCase = true)) {
                                println("Loading WFT: " + itemName)
                                mdl = Model().loadWFT(br, item.size)
                            }
                            val texName = ideList[i].textureName + ".wtd"
                            val texItem = fm.imgs[imgNumber].findItem(texName)
                            if (texItem != null) {
                                val txd = imageDictionaries.dictionaryAt(texItem.offset)
                                mdl?.attachTXD(txd)
                            }
                            // the model knows its own dictionary (embedded in the .wdr) and now the one
                            // of its .ide entry as well: hand both to GL and let the shader names match
                            mdl?.bindTextures(glTextures)
                        } catch (t: Throwable) {
                            println("Failed to load " + itemName + ": " + t)
                            mdl = null
                        }
                        glDisplayList[i + 1] = gl.glGenLists(1)
                        gl.glNewList(glDisplayList[i + 1], GL2.GL_COMPILE)
                        try {
                            if (mdl != null) {
                                mdl.render(gl)
                            } else {
                                drawCube(gl, 10f, 0.1f, 0.5f, 0.9f)
                            }
                        } catch (t: Throwable) {
                            println("Failed to render " + itemName + ": " + t)
                        }
                        gl.glEndList()
                        mdl?.reset()
                        boolList[i] = true
                    }
                }
            }
            rf.closeFile()
            glTextures.onNameMissing = null
            imageDictionaries.clear()
        }

        println("textures: " + glTextures.uploadedTextures + " uploaded ("
            + glTextures.uploadedBytes / 1024 + " KiB) from " + glTextures.dictionariesUsed
            + " dictionaries, " + glTextures.bound + " shaders textured (" + glTextures.fromImage
            + " from another dictionary of the image), " + glTextures.missing + " not found ("
            + glTextures.missingNameCount + " of " + glTextures.requestedCount + " distinct names)")
        if (glTextures.missingExamples.isNotEmpty()) {
            println("   names no attached dictionary has: " + glTextures.missingExamples.joinToString(", "))
        }
        progressDone = progressTotal
        progressListener?.invoke(progressDone, progressTotal)
    }

    private fun loadAddedModel(gl: GL2) {
        val tempList = glDisplayList // store all glInts to a temp array
        glDisplayList = IntArray(tempList.size + 1)
        for (i in tempList.indices) {
            glDisplayList[i] = tempList[i]
        }
        var item: ImgItem? = null
        var imgID = -1
        var i = 0
        while (item == null || i < fm.imgs.size) {
            if (tempIDE!!.WDD != "null") item = fm.imgs[i].findItem(tempIDE!!.WDD + ".wdd") else {
                item = fm.imgs[i].findItem(tempIDE!!.modelName + ".wdr")
                if (item == null) item = fm.imgs[i].findItem(tempIDE!!.modelName + ".wft")
            }
            if (item != null) imgID = i
            i++
        }
        if (item != null) {
            val rf = ReadFunctions(fm.imgs[imgID].fileName)
            rf.seek(item.offset)
            var br: ByteReader? = rf.getByteReader(item.size)
            var mdl: Model? = null
            if (item.name.endsWith(".wdr")) {
                println(item.name)
                mdl = Model().loadWDR(br, item.size) // load the
                // model
                // from img
            } else if (item.name.endsWith(".wdd")) {
                mdl = Model().loadWDD(br, item.size, tempIDE!!.modelName)
            } else if (item.name.endsWith(".wft")) {
                println("Loading WFT: " + item.name)
                mdl = Model().loadWFT(br, item.size)
            }
            br = null
            item = fm.imgs[imgID].findItem(tempIDE!!.textureName + ".wtd")
            if (item != null) {
                rf.seek(item.offset)
                br = rf.getByteReader(item.size)
                val txd = TextureDic(tempIDE!!.textureName + ".wtd", br, gameType, true, item.size)
                mdl?.attachTXD(txd)
            }
            val glTextures = textureCache ?: GlTextureCache(gl).also { textureCache = it }
            mdl?.bindTextures(glTextures)
            glDisplayList[tempList.size] = gl.glGenLists(1)
            gl.glNewList(glDisplayList[tempList.size], GL2.GL_COMPILE)
            if (mdl != null) {
                mdl.render(gl)
            } else {
                drawCube(gl, 10f, 0.1f, 0.5f, 0.9f)
            }
            gl.glEndList()
            mdl!!.reset()
            rf.closeFile()
            tempIPL!!.glListID = tempList.size
        } else {
            tempIPL!!.glListID = 0
        }
        tempIDE = null
        tempIPL = null
    }

    private fun getDistance(test1: Vector3D, test2: Vector3D): Float {
        val dx = test1.x - test2.x
        val dy = 0 - test1.z - test2.y
        val dz = test1.y - test2.z
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

    fun addLoadModelToLoad(tempIPL: Item_INST?, tempIDE: ItemObject?) {
        this.tempIPL = tempIPL
        this.tempIDE = tempIDE
        added = true
    }
}