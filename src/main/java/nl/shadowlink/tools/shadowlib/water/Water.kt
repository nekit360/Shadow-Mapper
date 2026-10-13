package nl.shadowlink.tools.shadowlib.water

import nl.shadowlink.tools.shadowlib.utils.GameType
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * One `water.dat`: every content line is a water plane.
 *
 * Blank lines and comments are skipped, and a line no plane can be built from is reported and skipped instead
 * of taking the load down. This file is read **last** in the load, so an exception here used to leave the
 * editor's loading dialog open forever: on the modded install `ArrayIndexOutOfBoundsException` in `WaterPoint`
 * killed the loader thread right before the WPL pass and the dialog stopped at 96 % (see `NEXT-STEPS.md`,
 * item 6). The old reader also printed every one of the 403 lines of the stock file.
 *
 * @author Shadow-Link
 */
class Water(
    val fileName: String,
    val gameType: GameType
) {

    @JvmField
    var planes: ArrayList<WaterPlane> = ArrayList()

    /** lines that carry no plane (blank, a comment) */
    var skippedLines = 0
        private set

    /** lines a plane could not be built from, each one named in the log */
    var failedLines = 0
        private set

    init {
        read()
    }

    fun read() {
        val input = BufferedReader(FileReader(fileName))

        try {
            var line: String?
            var lineNumber = 0
            while (input.readLine().also { line = it } != null) {
                lineNumber++
                val text = line!!.trim()
                if (text.isEmpty() || text.startsWith("#")) {
                    skippedLines++
                    continue
                }
                try {
                    planes.add(WaterPlane(text))
                } catch (ex: Exception) {
                    failedLines++
                    val where = "$fileName line $lineNumber"
                    println("Warning: $where: skipped a water plane (${ex.javaClass.simpleName}: ${ex.message})")
                }
            }
            println(
                "$fileName: ${planes.size} water planes" +
                        (if (failedLines > 0) ", $failedLines lines skipped" else "") +
                        (if (skippedLines > 0) " ($skippedLines empty or comment lines)" else "")
            )
        } catch (ex: IOException) {
            Logger.getLogger(Water::class.java.name).log(Level.SEVERE, null, ex)
        }
        input.close()
    }
}
