package nl.shadowlink.tools.shadowmapper.gui.checklist

import java.awt.event.*
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

// @author Santhosh Kumar T - santhosh@in.fiorano.com
class CheckListManager<T>(
    private val list: JList<T>,
    private val onSelectionToggled: (index: Int, isSelected: Boolean) -> Unit,
) : MouseAdapter(), ListSelectionListener, ActionListener {

    private val selectionModel: ListSelectionModel = DefaultListSelectionModel()
    var hotspot = JCheckBox().preferredSize.width

    init {
        list.cellRenderer = CheckListCellRenderer(list.cellRenderer, selectionModel)
        list.registerKeyboardAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED)
        list.addMouseListener(this)
        selectionModel.addListSelectionListener(this)
    }

    private fun toggleSelection(index: Int) {
        if (index < 0) return
        if (selectionModel.isSelectedIndex(index)) {
            selectionModel.removeSelectionInterval(index, index)
            onSelectionToggled(index, false)
        } else {
            selectionModel.addSelectionInterval(index, index)
            onSelectionToggled(index, true)
        }
    }

    override fun mouseClicked(me: MouseEvent) {
        val index = list.locationToIndex(me.point)
        if (index < 0) return
        if (me.x > list.getCellBounds(index, index).x + hotspot) return
        toggleSelection(index)
    }

    /** Tick or untick an entry from the outside, keeping the painted checkbox in sync. */
    fun setSelected(index: Int, selected: Boolean) {
        if (index < 0) return
        if (selectionModel.isSelectedIndex(index) == selected) return
        if (selected) {
            selectionModel.addSelectionInterval(index, index)
        } else {
            selectionModel.removeSelectionInterval(index, index)
        }
        onSelectionToggled(index, selected)
    }

    /**
     * Tick or untick every entry at once (batch selection for the list checkboxes).
     *
     * Only entries whose state actually changes are reported to [onSelectionToggled], and the change is
     * applied to the selection model in one step so the list is repainted once instead of once per row.
     *
     * @param maxEntries how many entries to touch - the caller may know that the list holds more rows
     *        than there are objects behind them (the list is filled by the loading thread)
     */
    fun setAllSelected(selected: Boolean, maxEntries: Int = Int.MAX_VALUE) {
        val entries = minOf(maxEntries, list.model.size)
        if (entries <= 0) return
        val changed = (0 until entries).filter { selectionModel.isSelectedIndex(it) != selected }
        if (changed.isEmpty()) return
        if (selected) {
            selectionModel.addSelectionInterval(0, entries - 1)
        } else {
            selectionModel.removeSelectionInterval(0, entries - 1)
        }
        changed.forEach { onSelectionToggled(it, selected) }
        list.repaint()
    }

    override fun valueChanged(e: ListSelectionEvent) {
        list.repaint(list.getCellBounds(e.firstIndex, e.lastIndex))
    }

    override fun actionPerformed(e: ActionEvent) {
        toggleSelection(list.selectedIndex)
    }
}