package gitea_plugin.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import gitea_plugin.GitUtils
import gitea_plugin.GlobalGiteaCache
import io.gitea.model.PullRequest
import java.awt.BorderLayout
import javax.swing.table.DefaultTableModel

class PRDetailsPanel(private val project: Project) : JBPanel<PRDetailsPanel>(BorderLayout()) {
    private val tableModel = object : DefaultTableModel(arrayOf("Property", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = object : JBTable(tableModel) {
        override fun getScrollableTracksViewportWidth(): Boolean = true
    }
    private val pane = JBScrollPane(table)
    private var currentPR: PullRequest? = null
    private val gitUtils = GitUtils(project)

    init {
        add(pane, BorderLayout.CENTER)

        GlobalGiteaCache.addListener { pr ->
            ApplicationManager.getApplication().invokeLater {
                updateDetails(pr)
            }
        }
    }

    fun updateDetails(selectedPR: PullRequest?) {
        currentPR = selectedPR

//        pane.border = BorderFactory.createTitledBorder("Details: ${selectedPR?.title ?: "No PR selected"}")
        tableModel.rowCount = 0
        if (selectedPR == null) {
            revalidate()
            repaint()
            return
        }

        tableModel.addRow(arrayOf("Title", selectedPR.title ?: "No title?"))
        tableModel.addRow(arrayOf("State", selectedPR.state ?: ""))
        tableModel.addRow(arrayOf("Description", selectedPR.body ?: ""))
        tableModel.addRow(arrayOf("Author", selectedPR.user?.login ?: ""))
        tableModel.addRow(arrayOf("Assignee", selectedPR.assignee?.login ?: ""))
        tableModel.addRow(arrayOf("Labels", selectedPR.labels?.joinToString { it.name } ?: ""))
        tableModel.addRow(arrayOf("Milestone", selectedPR.milestone?.title ?: ""))
        tableModel.addRow(arrayOf("Comment Threads", selectedPR.comments?.toString() ?: "0"))
        tableModel.addRow(arrayOf("Branch", selectedPR.head.ref ?: ""))

        revalidate()
        repaint()
    }

}
