package nl.shadowlink.tools.shadowmapper.render

import com.jogamp.opengl.GL2
import nl.shadowlink.tools.io.ByteReader
import nl.shadowlink.tools.io.ReadFunctions
import nl.shadowlink.tools.io.Vector3D
import nl.shadowlink.tools.shadowlib.ide.ItemObject
import nl.shadowlink.tools.shadowlib.img.ImgItem
import nl.shadowlink.tools.shadowlib.ipl.Item_INST
import nl.shadowlink.tools.shadowlib.model.model.Element
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

    /**
     * The transparent materials of the same models, in parallel with [glDisplayList]: entry `listID` holds
     * the display list with only the glass of that model, 0 when the model has none. A model's glass is in
     * this list and in no other, so the pane is not drawn by the opaque pass and can blend with what stands
     * behind it (see [drawTransparentPass]).
     */
    private var glTransparentList: IntArray = IntArray(0)

    /** the glass instances of the frame being drawn, collected by the opaque pass */
    private val glassInstances = ArrayList<GlassInstance>()

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
    /**
     * How many instances the last frame walked in the opaque loop: every instance of every IPL that is
     * ticked and loaded, whether or not it ends up being drawn. It is the per-frame cost of a whole-map
     * load, and it is what the draw distance test spares the card.
     */
    var instancesVisited = 0
        private set
    /** How many of those the draw distance let through to a display list (the transparent pass aside). */
    var instancesDrawn = 0
        private set
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
            glassInstances.clear()
            instancesVisited = 0
            instancesDrawn = 0
            gl.glPushName(PickingType.map)
            fm.ipls.forEachIndexed { iplIndex, ipl ->
                if (ipl.selected && ipl.itemsLoaded) {
                    gl.glPushName(iplIndex)
                    ipl.itemsInst.forEachIndexed { instanceIndex, item ->
                        instancesVisited++
                        // The draw distance test comes first: it is three subtractions and a comparison, and
                        // it is false for the great majority of the instances of a whole-map load (175 447 of
                        // them), so their model name is never looked at and no string is built for them. The
                        // two tests are the ones that were here before, in the other order and without the
                        // square root; a LOD is still skipped and a far instance is still skipped.
                        if (fullDrawDistance || isWithinDrawDistance(item)) {
                            if (!item.isLodModel()) {
                                gl.glPushName(instanceIndex)
                                if (item.selected) {
                                    gl.glColor3f(0.9f, 0f, 0f)
                                } else {
                                    gl.glColor3f(1f, 1f, 1f)
                                }
                                drawInstance(gl, item, glDisplayList)
                                instancesDrawn++
                                gl.glPopName()
                                // this instance's glass is in no other list: it is drawn at the end of the
                                // frame, over every opaque material, so it blends with what stands behind it
                                val glassList = glassListId(item)
                                if (glassList != 0) {
                                    glassInstances.add(
                                        GlassInstance(iplIndex, instanceIndex, glassList,
                                            getDistance(camera.pos, item.position), item)
                                    )
                                }
                            }
                        }
                    }
                    gl.glPopName()
                }
            }
            gl.glPopName()
            drawTransparentPass(gl)
        }
        gl.glFlush()
    }

    /** One instance whose model carries glass, collected while the opaque pass draws the frame. */
    private class GlassInstance(
        val iplIndex: Int,
        val instanceIndex: Int,
        val listId: Int,
        val distance: Float,
        val item: Item_INST,
    )

    /** The glass display list of an instance's model, 0 when it has none. */
    private fun glassListId(item: Item_INST): Int {
        val listId = item.glListID
        return if (listId > 0 && listId < glTransparentList.size) glTransparentList[listId] else 0
    }

    /** Draw one instance of the map: the transform of the instance and the display list of its model. */
    private fun drawInstance(gl: GL2, item: Item_INST, list: IntArray) {
        gl.glPushMatrix()
        gl.glTranslatef(item.position.x, item.position.y, item.position.z)
        gl.glRotatef(item.axisAngle.w, item.axisAngle.x, item.axisAngle.y, item.axisAngle.z)
        gl.glCallList(list[item.glListID])
        gl.glPopMatrix()
    }

    /**
     * The second pass of a frame: every instance whose model has glass, blended, two-sided and sorted far to
     * near, over the opaque material the first pass left behind.
     *
     * The glass is in no other display list, so a pane blends with the interior, the wall or the street that
     * stands behind it - the thing the game blends it with - instead of with the colour the view was cleared
     * with. Depth *testing* stays on, so a wall in front of the pane still hides it; depth *writing* is off,
     * so two panes behind one another can blend with each other, and so a pane cannot hide the interior it is
     * supposed to show.
     */
    private fun drawTransparentPass(gl: GL2) {
        if (glassInstances.isEmpty()) return
        glassInstances.sortByDescending { it.distance }
        gl.glEnable(GL2.GL_DEPTH_TEST)
        gl.glEnable(GL2.GL_BLEND)
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA)
        gl.glDepthMask(false)
        gl.glDisable(GL2.GL_ALPHA_TEST)
        gl.glDisable(GL2.GL_CULL_FACE)
        gl.glPushName(PickingType.map)
        glassInstances.forEach { glass ->
            gl.glPushName(glass.iplIndex)
            gl.glPushName(glass.instanceIndex)
            if (glass.item.selected) {
                gl.glColor3f(0.9f, 0f, 0f)
            } else {
                gl.glColor3f(1f, 1f, 1f)
            }
            drawInstance(gl, glass.item, glTransparentList)
            gl.glPopName()
            gl.glPopName()
        }
        gl.glPopName()
        // the pass must not leak into what is drawn after the map (the water, the next frame): both are
        // state a display list elsewhere may never set for itself. The polygon offset belongs to the decal
        // materials inside the lists, but the last one may leave it on.
        gl.glDepthMask(true)
        gl.glEnable(GL2.GL_CULL_FACE)
        gl.glDisable(GL2.GL_POLYGON_OFFSET_FILL)
    }

    private fun loadMap(gl: GL2) {
        // Clear existing display list
        glDisplayList.forEach { dl -> gl.glDeleteLists(dl, 1) }
        glTransparentList.forEach { dl -> gl.glDeleteLists(dl, 1) }
        // and the textures of the previous load: every load builds new dictionaries
        textureCache?.dispose()
        val glTextures = GlTextureCache(gl)
        textureCache = glTextures

        // progress is reported on a fixed 0..1000 scale: phase 1 (resolving the instances of the
        // selected IPLs against the IDEs) gets the first half, phase 2 (looking for every model in
        // every image) the second, so the bar never jumps backwards when the phases change. The
        // split follows the measured times (on a large selection both phases run about 14 s); it
        // used to be 10/90, which parked the bar at "0 %" for the whole slow instance walk because
        // the printed percent only left 0 after the first 5 % of the instances were done.
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
                    if (instancesSeen % 100 == 0) {
                        progressDone = instancesSeen * 500 / instancesTotal
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
        glTransparentList = IntArray(ideList.size + 1)
        println("ideList Size: " + ideList.size)
        // the rest of the work is scanning every image for every model they may contain
        val phase2Total = (fm.imgs.size * ideList.size).coerceAtLeast(1)
        var phase2Seen = 0
        progressDone = 500
        progressListener?.invoke(progressDone, progressTotal)
        // a name that neither the model nor its own image has may live in a neighbouring image (the game
        // streams the district around it) - see NeighbourDictionaries, which lives for the whole load so
        // that an image that had to be searched once is not searched again for the next district
        val neighbours = NeighbourDictionaries(fm, gameType)
        var fromNeighbour = 0
        var dictionariesParsed = 0
        var dictionaryIndexHits = 0
        Element.resetTextureDiagnostics()
        val whiteModels = ArrayList<String>()
        var modelsWithGlass = 0
        for (imgNumber in fm.imgs.indices) {
            val currentImage = fm.imgs[imgNumber].fileName
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
            glTextures.onNameMissing = { name ->
                val dictionary = imageDictionaries.dictionaryWith(name)
                if (dictionary != null) dictionary
                else neighbours.dictionaryWith(name, currentImage)?.also { fromNeighbour++ }
            }
            for (i in ideList.indices) {
                phase2Seen++
                if (phase2Seen % 100 == 0) {
                    progressDone = 500 + phase2Seen * 500 / phase2Total
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
                            val noNameBefore = Element.shadersWithoutTextureName
                            val nameMissBefore = Element.shadersWithoutTexture
                            mdl?.bindTextures(glTextures)
                            val noName = Element.shadersWithoutTextureName - noNameBefore
                            val nameMiss = Element.shadersWithoutTexture - nameMissBefore
                            if (noName + nameMiss > 0 && whiteModels.size < 16) {
                                whiteModels.add(itemName + " (no diffuse parameter: " + noName
                                    + ", name not found: " + nameMiss + ")")
                            }
                        } catch (t: Throwable) {
                            println("Failed to load " + itemName + ": " + t)
                            mdl = null
                        }
                        glDisplayList[i + 1] = gl.glGenLists(1)
                        gl.glNewList(glDisplayList[i + 1], GL2.GL_COMPILE)
                        try {
                            if (mdl != null) {
                                // everything but the glass: the transparent materials get a list of their own,
                                // because they are drawn at the end of the frame and nowhere else
                                mdl.renderOpaque(gl)
                            } else {
                                drawCube(gl, 10f, 0.1f, 0.5f, 0.9f)
                            }
                        } catch (t: Throwable) {
                            println("Failed to render " + itemName + ": " + t)
                        }
                        gl.glEndList()
                        if (mdl != null && mdl.hasTransparentMaterials()) {
                            val glassList = gl.glGenLists(1)
                            gl.glNewList(glassList, GL2.GL_COMPILE)
                            var glassOk = true
                            try {
                                mdl.renderTransparent(gl)
                            } catch (t: Throwable) {
                                // a half-compiled list would be drawn in the transparent pass, so drop it
                                println("Failed to render the glass of " + itemName + ": " + t)
                                glassOk = false
                            }
                            gl.glEndList()
                            if (glassOk) {
                                glTransparentList[i + 1] = glassList
                                modelsWithGlass++
                            } else {
                                gl.glDeleteLists(glassList, 1)
                            }
                        }
                        mdl?.reset()
                        boolList[i] = true
                    }
                }
            }
            rf.closeFile()
            glTextures.onNameMissing = null
            dictionariesParsed += imageDictionaries.parsedDictionaries
            dictionaryIndexHits += imageDictionaries.indexHits
            imageDictionaries.clear()
        }

        // the log names the images that were searched, and clear() releases them (their caches, their open
        // files): take the names and the counters before it, or the summary prints empty parentheses
        val searchedImages = neighbours.searched.toList()
        val globalImages = neighbours.globals.toList()
        val hitsFromGlobal = neighbours.hitsFromGlobal
        neighbours.clear()
        println("textures: " + glTextures.uploadedTextures + " uploaded ("
            + glTextures.uploadedBytes / 1024 + " KiB) from " + glTextures.dictionariesUsed
            + " dictionaries, " + glTextures.bound + " shaders textured (" + glTextures.fromImage
            + " from another dictionary of the image), " + glTextures.missing + " not found ("
            + glTextures.missingNameCount + " of " + glTextures.requestedCount + " distinct names)")
        // how often a dictionary had to be parsed for the models of this load: a TXD that several models
        // name is parsed once per model, and the index of a dictionary answers a name without parsing it
        println("   " + dictionariesParsed + " dictionary parses, " + dictionaryIndexHits
            + " names answered by an index of this image")
        if (glTextures.missingExamples.isNotEmpty()) {
            println("   names no attached dictionary has: " + glTextures.missingExamples.joinToString(", "))
        }
        if (fromNeighbour > 0 || searchedImages.isNotEmpty()) {
            println("   " + (fromNeighbour - hitsFromGlobal)
                + " shaders textured from a neighbouring image ("
                + searchedImages.joinToString(", ") + ")")
        }
        if (hitsFromGlobal > 0) {
            println("   " + hitsFromGlobal + " from the global texture pack ("
                + globalImages.joinToString(", ") + ")")
        }
        println("   shader slots without a diffuse map: " + Element.shadersWithoutTextureName
            + ", with a name no dictionary has: " + Element.shadersWithoutTexture)
        if (Element.transparentShaders > 0) {
            println("   drawn in the transparent pass: " + Element.transparentShaders
                + " shader slots on " + modelsWithGlass + " models (glass " + Element.transparentGlass
                + ", *_alpha presets " + Element.transparentAlphaPresets
                + ", decals " + Element.transparentDecals
                + ", transparent diffuse map " + Element.transparentTextures + ")")
        }
        if (Element.unknownShaderParams.isNotEmpty()) {
            Element.unknownShaderParams.forEach { println("   " + it) }
        }
        if (whiteModels.isNotEmpty()) {
            println("   models that got a shader without a texture: " + whiteModels.joinToString(", "))
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
        val tempGlass = glTransparentList
        glTransparentList = IntArray(tempGlass.size + 1)
        for (i in tempGlass.indices) {
            glTransparentList[i] = tempGlass[i]
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
            // the item variable is reassigned to the model's .wtd below, so keep the model's name here
            val loadedModelName = item.name
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
                mdl.renderOpaque(gl)
            } else {
                drawCube(gl, 10f, 0.1f, 0.5f, 0.9f)
            }
            gl.glEndList()
            if (mdl != null && mdl.hasTransparentMaterials()) {
                val glassList = gl.glGenLists(1)
                gl.glNewList(glassList, GL2.GL_COMPILE)
                var glassOk = true
                try {
                    mdl.renderTransparent(gl)
                } catch (t: Throwable) {
                    println("Failed to render the glass of " + loadedModelName + ": " + t)
                    glassOk = false
                }
                gl.glEndList()
                if (glassOk) {
                    glTransparentList[tempList.size] = glassList
                } else {
                    gl.glDeleteLists(glassList, 1)
                }
            }
            mdl!!.reset()
            rf.closeFile()
            tempIPL!!.glListID = tempList.size
        } else {
            tempIPL!!.glListID = 0
        }
        tempIDE = null
        tempIPL = null
    }

    /**
     * The draw distance test of the frame loop, without the square root: the loop only compares distances
     * and paid for 175 447 roots per frame otherwise. Same test as `getDistance(...) < item.drawDistance`.
     */
    private fun isWithinDrawDistance(item: Item_INST): Boolean {
        val dx = camera.pos.x - item.position.x
        val dy = 0 - camera.pos.z - item.position.y
        val dz = camera.pos.y - item.position.z
        val limit = item.drawDistance
        return dx * dx + dy * dy + dz * dz < limit * limit
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