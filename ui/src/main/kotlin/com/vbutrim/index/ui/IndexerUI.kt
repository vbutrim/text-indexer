package com.vbutrim.index.ui

import com.vbutrim.file.AbsolutePath
import com.vbutrim.index.DocumentsIndexer
import com.vbutrim.index.IndexedItem
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

class IndexerUI(override val documentsIndexer: DocumentsIndexer) : JFrame("TextIndexer"), Indexer {

    private val tokensInput = JTextField(20).apply {
        toolTipText = "comma separated"
    }
    private val addPathsToIndexButton = JButton("Add paths")
    private val removePathsFromIndexButton = JButton("Remove paths")
    private val indexedDocuments = IndexedDocuments()
    private val userSelectionOnlyCheckBox = JCheckBox("user selection only")
    private val searchButton = JButton("Search documents")
    private val searchResults = SearchResults()
    private val statusBar = StatusBar()

    override val job = Job()

    init {
        UIManager.put("FileChooser.readOnly", true)
        rootPane.contentPane = JPanel(GridBagLayout()).apply {
            addWide(JPanel().apply {
                add(addPathsToIndexButton)
                add(removePathsFromIndexButton)
            })
            addWide(indexedDocuments.scroll) {
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
            addWide(userSelectionOnlyCheckBox)
            addWideSeparator()
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
            addWide(statusBar.bar)
        }

        init()
    }

    override fun updateDocumentsThatContainTerms(documents: List<AbsolutePath>) {
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
        addPathsToIndexButton.addActionListener { listener() }
    }

    override fun getDocumentsToIndex(): List<AbsolutePath> {
        val fileChooser = JFileChooser()

        fileChooser.currentDirectory = File(System.getProperty("user.home"))
        fileChooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        fileChooser.isMultiSelectionEnabled = true

        val result = fileChooser.showOpenDialog(this)

        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFiles = fileChooser.selectedFiles
                .map { AbsolutePath.cons(it.toPath()) }
                .toList()
            log.debug("Selected files: $selectedFiles")
            return selectedFiles
        }

        log.debug("Cancel to to select files")
        return Collections.emptyList()
    }

    override fun updateIndexedDocuments(documents: List<IndexedItem>) {
        indexedDocuments.updateWith(documents)
    }

    override fun addUserSelectionOnlyListener(listener: () -> Unit) {
        userSelectionOnlyCheckBox.addActionListener { listener() }
    }

    override fun showOnlySelectedByUserIndexedDocuments(): Boolean {
        return userSelectionOnlyCheckBox.isSelected
    }

    override fun addRemoveDocumentsToIndexListener(listener: () -> Unit) {
        removePathsFromIndexButton.addActionListener { listener() }
    }

    override fun getDocumentsToIndexToRemove(): Indexer.ToRemove {
        val toRemove = indexedDocuments.getSelectedPaths()
        return Indexer.ToRemove(toRemove.first, toRemove.second)
    }

    override fun setActionsStatus(newSearchIsEnabled: Boolean, newIndexingIsEnabled: Boolean) {
        searchButton.isEnabled = newSearchIsEnabled
        addPathsToIndexButton.isEnabled = newIndexingIsEnabled
        removePathsFromIndexButton.isEnabled = newIndexingIsEnabled
        userSelectionOnlyCheckBox.isEnabled = newIndexingIsEnabled
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
                log.debug("Updating result with ${items.size} rows")
            } else {
                log.debug("Clearing result")
            }
            tableModel.setDataVector(
                items.toTypedArray(),
                columns
            )
        }

        protected fun getSelectedItems(@Suppress("SameParameterValue") column: Int): List<String> {
            return results.selectedRows
                .map {row -> results.getValueAt(row, column).toString() }
        }
    }

    private class SearchResults : NonEditableListElement(SEARCH_RESULTS_COLUMNS) {
        fun updateWith(documents: List<AbsolutePath>) {
            super.set(documents.map { arrayOf(it.toString()) })
        }
    }

    private class IndexedDocuments : NonEditableListElement(INDEXED_DOCUMENTS_COLUMNS) {
        companion object {
            private const val DIR_PREFIX: String = "[dir] "
        }
        fun updateWith(indexedItems: List<IndexedItem>) {
            super.set(consRows(indexedItems).toList())
        }

        private fun consRows(indexedItems: List<IndexedItem>): Stream<Array<*>> {
            return indexedItems
                .stream()
                .flatMap { consRows(it) }
        }

        private fun consRows(indexedItem: IndexedItem): Stream<Array<String>> =
            when (indexedItem) {
                is IndexedItem.File -> {
                    Stream.of(arrayOf(indexedItem.getPathAsString()))
                }

                is IndexedItem.Dir -> {
                    Stream.concat(
                        Stream.of(arrayOf(DIR_PREFIX + indexedItem.getPathAsString())),
                        indexedItem.nested.stream().flatMap { consRows(it) }
                    )
                }
            }

        /**
         * @return selected files and dirs
         */
        @Suppress("SameParameterValue")
        fun getSelectedPaths(): Pair<List<AbsolutePath>, List<AbsolutePath>> {
            val selectedRows = getSelectedItems(0)
            val dirsAndFiles = selectedRows
                .map { Pair(AbsolutePath.cons(Path.of(it.removePrefix(DIR_PREFIX))), it.startsWith(DIR_PREFIX)) }
                .partition { it.second }

            return Pair(
                dirsAndFiles.second.map { it.first },
                dirsAndFiles.first.map { it.first }
            )
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