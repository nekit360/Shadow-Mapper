package nl.shadowlink.tools.shadowmapper.gui.checklist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.DefaultListModel
import javax.swing.JCheckBox
import javax.swing.JList

/**
 * The batch select of the Scene list checkboxes: ticking or unticking every entry at once must report
 * exactly the entries whose state changed (MainForm wires that callback to `fm.ipls[index].selected`) and
 * must keep the checkbox the cell renderer paints in sync.
 */
internal class CheckListManagerTest {

    private val entries = 5

    private val model = DefaultListModel<String>().apply {
        repeat(entries) { addElement("entry $it") }
    }
    private val list = JList(model)
    private val reported = mutableListOf<String>()

    private val manager = CheckListManager(list) { index, isSelected ->
        reported.add("$index=$isSelected")
    }

    private fun ticked(index: Int): Boolean {
        val cell = list.cellRenderer.getListCellRendererComponent(list, "entry $index", index, false, false)
        return findCheckBox(cell)?.isSelected ?: error("the cell renderer paints no checkbox")
    }

    private fun findCheckBox(component: Component): JCheckBox? {
        if (component is JCheckBox) return component
        if (component is Container) {
            for (child in component.components) {
                findCheckBox(child)?.let { return it }
            }
        }
        return null
    }

    @Test
    fun `when nothing is ticked, then select all reports every entry once`() {
        manager.setAllSelected(true)

        assertEquals((0 until entries).map { "$it=true" }, reported)
        assertTrue((0 until entries).all { ticked(it) })
    }

    @Test
    fun `when everything is already ticked, then select all reports nothing`() {
        manager.setAllSelected(true)
        reported.clear()

        manager.setAllSelected(true)

        assertEquals(emptyList<String>(), reported)
        assertTrue((0 until entries).all { ticked(it) })
    }

    @Test
    fun `when everything is ticked, then deselect all clears it and reports every entry`() {
        manager.setAllSelected(true)
        reported.clear()

        manager.setAllSelected(false)

        assertEquals((0 until entries).map { "$it=false" }, reported)
        assertTrue((0 until entries).none { ticked(it) })
    }

    @Test
    fun `when only some entries are ticked, then select all reports only the rest`() {
        manager.setSelected(0, true)
        manager.setSelected(1, true)
        reported.clear()

        manager.setAllSelected(true)

        assertEquals(listOf("2=true", "3=true", "4=true"), reported)
        assertTrue((0 until entries).all { ticked(it) })
    }

    @Test
    fun `when the caller knows fewer objects than rows, then the extra rows are left alone`() {
        manager.setAllSelected(true, maxEntries = 2)

        assertEquals(listOf("0=true", "1=true"), reported)
        assertTrue(ticked(0) && ticked(1))
        assertFalse(ticked(2))
    }

    @Test
    fun `when the list is empty, then select all does nothing`() {
        val empty = JList(DefaultListModel<String>())
        var calls = 0
        val emptyManager = CheckListManager(empty) { _, _ -> calls++ }

        emptyManager.setAllSelected(true)

        assertEquals(0, calls)
    }
}
