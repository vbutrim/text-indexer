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
    private val resultsModel = DefaultTableModel(COLUMNS, 0)
    private val results = JTable(resultsModel)
    private val resultsScroll = JScrollPane(results).apply {
        preferredSize = Dimension(200, 200)
    }

    override val job = Job()

    override fun updateDocumentsThatContainsTerms(documents: List<Path>) {
        if (documents.isNotEmpty()) {
            log.info("Updating result with ${documents.size} rows")
        } else {
            log.info("Clearing result")
        }
        resultsModel.setDataVector(
            documents
                .map { arrayOf(it.toString()) }
                .toTypedArray(),
            COLUMNS
        )
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
            addWide(resultsScroll) {
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
/*            addLabeled("Password/Token", password)
            addWideSeparator()
            addLabeled("Organization", org)
            addLabeled("Variant", variant)*/
            addWideSeparator()
/*            addWide(JPanel().apply {
                add(load)
                add(cancel)
            })
            addWide(loadingStatus)*/
        }

        init()
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
            .split(",")
            .map { it.trim().lowercase() }
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