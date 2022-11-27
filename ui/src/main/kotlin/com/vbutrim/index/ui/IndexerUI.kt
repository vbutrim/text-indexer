package com.vbutrim.index.ui

import kotlinx.coroutines.Job
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import javax.swing.*
import javax.swing.table.DefaultTableModel

private val INSETS = Insets(3, 10, 3, 10)
private val COLUMNS = arrayOf("Documents with terms")

class IndexerUI : JFrame("TextIndexer"), Indexer {

    private val terms = JTextField(20).apply {
        toolTipText = "comma separated"
    }
    private val search = JButton("Search documents")
    private val searchResults = SearchResults()
    private val statusBar = StatusBar()

    override val job = Job()

    override fun updateDocumentsThatContainsTerms(documents: List<Path>) {
        searchResults.updateWith(documents)
    }

    override fun addSearchListener(listener: () -> Unit) {
        search.addActionListener { listener() }
    }

    init {
        // Create UI
        rootPane.contentPane = JPanel(GridBagLayout()).apply {
            addLabeled("Terms", terms)
            addWide(JPanel().apply {
                add(search)
            })
            addWide(searchResults.scroll) {
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
            addWideSeparator()
            addWide(statusBar.bar)
/*            addWide(JPanel().apply {
                add(load)
                add(cancel)
            })
            addWide(loadingStatus)*/
        }

        init()
    }

    override fun setStatus(text: String, iconRunning: Boolean) {
        statusBar.updateWith(text, iconRunning)
    }

    override fun addOnWindowClosingListener(listener: () -> Unit) {
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                listener()
            }
        })
    }

    override fun getTokensToSearch(): List<String> {
        return terms.text
            .trim()
            .lowercase()
            .split(",")
            .filter { it.isNotEmpty() }
    }

    private class SearchResults {
        private val tableModel = NonEditableTableModel()
        private class NonEditableTableModel : DefaultTableModel(COLUMNS, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean {
                return false
            }
        }
        private val results = JTable(tableModel)
        val scroll = JScrollPane(results).apply {
            preferredSize = Dimension(200, 200)
        }

        fun updateWith(documents: List<Path>) {
            if (documents.isNotEmpty()) {
                log.info("Updating result with ${documents.size} rows")
            } else {
                log.info("Clearing result")
            }
            tableModel.setDataVector(
                documents
                    .map { arrayOf(it.toString()) }
                    .toTypedArray(),
                COLUMNS
            )
        }
    }

    private class StatusBar {
        private val icon = ImageIcon(javaClass.classLoader.getResource("ajax-loader.gif"))
        val bar = JLabel("", null, SwingConstants.CENTER)

        fun updateWith(text: String, iconRunning: Boolean) {
            bar.text = text
            bar.icon = if (iconRunning) icon else null
        }
    }
}

fun JPanel.addLabeled(label: String, component: JComponent) {
    add(JLabel(label), GridBagConstraints().apply {
        gridx = 0
        insets = INSETS
    })
    add(component, GridBagConstraints().apply {
        gridx = 1
        insets = INSETS
        anchor = GridBagConstraints.WEST
        fill = GridBagConstraints.HORIZONTAL
        weightx = 1.0
    })
}

fun JPanel.addWideSeparator() {
    addWide(JSeparator()) {
        fill = GridBagConstraints.HORIZONTAL
    }
}

fun JPanel.addWide(component: JComponent, constraints: GridBagConstraints.() -> Unit = {}) {
    add(component, GridBagConstraints().apply {
        gridx = 0
        gridwidth = 2
        insets = INSETS
        constraints()
    })
}

fun setDefaultFontSize(size: Float) {
    for (key in UIManager.getLookAndFeelDefaults().keys.toTypedArray()) {
        if (key.toString().lowercase().contains("font")) {
            val font = UIManager.getDefaults().getFont(key) ?: continue
            val newFont = font.deriveFont(size)
            UIManager.put(key, newFont)
        }
    }
}