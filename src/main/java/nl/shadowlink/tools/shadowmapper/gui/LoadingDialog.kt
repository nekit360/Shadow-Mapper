package nl.shadowlink.tools.shadowmapper.gui

import nl.shadowlink.tools.shadowmapper.LoadingStatusCallbacks
import nl.shadowlink.tools.shadowmapper.utils.GuiFunctions.centerWindow
import javax.swing.*

/**
 * @author Shadow-Link
 */
internal class LoadingDialog(private val initialStatus: String = "Loading: ") : JDialog(), LoadingStatusCallbacks {

    private var loadingBar = JProgressBar()
    private var loadingLabel = JLabel()

    /**
     * Creates new form LoadingBar
     */
    init {
        initComponents()
        centerWindow()
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
//        modalityType = ModalityType.APPLICATION_MODAL
        isVisible = true
    }

    override fun onStartLoading(fileCount: Int) {
        loadingBar.value = 0
        loadingBar.minimum = 0
        loadingBar.maximum = fileCount
    }

    override fun onStartLoadingWpl(wplCount: Int) {
        loadingBar.value = 0
        loadingBar.minimum = 0
        loadingBar.maximum = wplCount
    }

    override fun onLoadingStatusChanged(status: String) {
        loadingLabel.text = status
    }

    override fun onLoadingValueIncreased() {
        loadingBar.value = loadingBar.value + 1
    }

    /**
     * Absolute progress for loads that can report it themselves, such as the map the Render button
     * builds: the bar is rescaled to the reported total instead of counting single files.
     */
    fun onLoadingProgress(done: Int, total: Int) {
        if (total <= 0) return
        loadingBar.minimum = 0
        loadingBar.maximum = total
        loadingBar.value = done
    }

    /**
     * Paints the dialog right now instead of queueing a repaint. The map load runs on the event
     * dispatch thread, so ordinary repaints are not flushed until it is over - without this the
     * dialog would stay empty (or never appear) for the whole load.
     */
    fun repaintNow() {
        val pane = contentPane as JComponent
        pane.paintImmediately(0, 0, pane.width, pane.height)
    }

    override fun onLoadingFinished() {
        dispose()
    }

    private fun initComponents() {
        loadingLabel = JLabel()
        loadingBar = JProgressBar()
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        title = "Loading"
        isResizable = false
        loadingLabel.text = initialStatus
        loadingBar.isStringPainted = true

        val layout = GroupLayout(contentPane)
        contentPane.layout = layout
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(
                    layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(loadingBar, GroupLayout.PREFERRED_SIZE, 280, GroupLayout.PREFERRED_SIZE)
                                .addComponent(loadingLabel)
                        )
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE.toInt())
                )
        )
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(
                    layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(loadingLabel)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(
                            loadingBar,
                            GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE,
                            GroupLayout.PREFERRED_SIZE
                        )
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE.toInt())
                )
        )
        pack()
    }
}