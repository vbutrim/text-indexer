package com.vbutrim.index.ui

import kotlinx.coroutines.Job
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.stream.Stream
import javax.swing.*
import javax.swing.table.DefaultTableModel

private val INSETS = Insets(3, 10, 3, 10)
private val SEARCH_RESULTS_COLUMNS = arrayOf("Documents that contain tokens")
private val INDEXED_DOCUMENTS_COLUMNS = arrayOf("Indexed documents")

class IndexerUI : JFrame("TextIndexer"), Indexer {

    private val tokensInput = JTextField(20).apply {
        toolTipText = "comma separated"
    }
    private val searchButton = JButton("Search documents")
    private val searchResults = SearchResults()
    private val addPathToIndexButton = JButton("Add paths")
    private val removePathFromIndexButton = JButton("Remove paths")
    private val indexedDocuments = IndexedDocuments()
    private val statusBar = StatusBar()

    override val job = Job()

    init {
        rootPane.contentPane = JPanel(GridBagLayout()).apply {
            addLabeled("Tokens", tokensInput)
            addWide(JPanel().apply {
                add(searchButton)
            })
            addWide(searchResults.scroll) {
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
            addWideSeparator()
            addWide(JPanel().apply {
                add(addPathToIndexButton)
                add(removePathFromIndexButton)
            })
            addWide(indexedDocuments.scroll) {
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
            addWideSeparator()
            addWide(statusBar.bar)
        }

        init()
    }

    override fun updateDocumentsThatContainTerms(documents: List<Path>) {
        searchResults.updateWith(documents)
    }

    override fun addSearchListener(listener: () -> Unit) {
        searchButton.addActionListener { listener() }
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

    override fun addGetDocumentsToIndexListener(listener: () -> Unit) {
        addPathToIndexButton.addActionListener { listener() }
    }

    override fun getDocumentsToIndex(): List<Path> {
        val fileChooser = JFileChooser()

        fileChooser.currentDirectory = File(System.getProperty("user.home"))
        fileChooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        fileChooser.isMultiSelectionEnabled = true

        val result = fileChooser.showOpenDialog(this)

        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFiles = fileChooser.selectedFiles
                .map { it.toPath() }
                .toList()
            log.debug("Selected files: $selectedFiles")
            return selectedFiles
        }

        log.debug("Cancel to to select files")
        return Collections.emptyList()
    }

    override fun updateIndexedDocuments(documents: List<com.vbutrim.index.IndexedDocuments.Item>) {
        indexedDocuments.updateWith(documents)
    }

    override fun setActionsStatus(newSearchIsEnabled: Boolean, newIndexingIsEnabled: Boolean) {
        searchButton.isEnabled = newSearchIsEnabled
        addPathToIndexButton.isEnabled = newIndexingIsEnabled
        removePathFromIndexButton.isEnabled = newIndexingIsEnabled
    }

    override fun getTokensToSearch(): List<String> {
        return tokensInput.text
            .trim()
            .lowercase()
            .split(",")
            .filter { it.isNotEmpty() }
    }

    private abstract class NonEditableListElement(private val columns: Array<String>) {
        private val tableModel = NonEditableTableModel(columns)
        private class NonEditableTableModel(columns: Array<String>) : DefaultTableModel(columns, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean {
                return false
            }
        }
        private val results = JTable(tableModel)
        val scroll = JScrollPane(results).apply {
            preferredSize = Dimension(200, 200)
        }

        protected fun set(items: List<Array<*>>) {
            if (items.isNotEmpty()) {
                log.info("Updating result with ${items.size} rows")
            } else {
                log.info("Clearing result")
            }
            tableModel.setDataVector(
                items.toTypedArray(),
                columns
            )
        }
    }

    private class SearchResults: NonEditableListElement(SEARCH_RESULTS_COLUMNS) {
        fun updateWith(documents: List<Path>) {
            super.set(documents.map { arrayOf(it.toString()) })
        }
    }

    private class IndexedDocuments: NonEditableListElement(INDEXED_DOCUMENTS_COLUMNS) {
        fun updateWith(indexedItems: List<com.vbutrim.index.IndexedDocuments.Item>) {
            super.set(consRows(indexedItems).toList())
        }

        private fun consRows(indexedItems: List<com.vbutrim.index.IndexedDocuments.Item>): Stream<Array<*>> {
            return indexedItems
                .stream()
                .flatMap { consRows(it) }
        }

        private fun consRows(indexedItem: com.vbutrim.index.IndexedDocuments.Item): Stream<Array<String>> = when (indexedItem) {
            is com.vbutrim.index.IndexedDocuments.File -> {
                Stream.of(arrayOf(indexedItem.getPathAsString()))
            }

            is com.vbutrim.index.IndexedDocuments.Dir -> {
                Stream.concat(
                    Stream.of(arrayOf("[dir] " + indexedItem.getPathAsString())),
                    indexedItem.nested.stream().flatMap { consRows(it) }
                )
            }
        }
    }

    private class StatusBar {
        private val icon = ImageIcon(javaClass.classLoader.getResource("ajax-loader.gif"))
        val bar = JLabel("Choose files/directories to index", null, SwingConstants.CENTER)

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