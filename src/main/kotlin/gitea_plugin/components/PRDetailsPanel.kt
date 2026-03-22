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
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

class PRDetailsPanel(project: Project) : JBPanel<PRDetailsPanel>(BorderLayout()) {
    private val tableModel = object : DefaultTableModel(arrayOf("Property", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = object : JBTable(tableModel) {
        override fun getScrollableTracksViewportWidth(): Boolean = true
    }
    private val pane = JBScrollPane(table)
    private var currentPR: PullRequest? = null
    private val gitUtils = GitUtils(project)

    private val checkoutButton = JButton().apply {
        isVisible = false
        addActionListener {
            val pr = currentPR ?: return@addActionListener
            val ref = pr.head?.ref ?: return@addActionListener

            ApplicationManager.getApplication().executeOnPooledThread {
                gitUtils.fetchAll()
                val isCurrent = gitUtils.isBranchCurrent(ref)
                ApplicationManager.getApplication().invokeLater {
                    val onFinished = {
                        updateButtonState(currentPR)
                    }
                    if (!isCurrent) {
                        gitUtils.checkoutBranch(ref) {
                            gitUtils.updateProject()
                            onFinished()
                        }
                    } else {
                        gitUtils.updateProject()
                        onFinished()
                    }
                }
            }
        }
    }

    init {
        val topPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        topPanel.add(checkoutButton)
        add(pane, BorderLayout.CENTER)
        add(topPanel, BorderLayout.SOUTH)


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

        updateButtonState(selectedPR)

        revalidate()
        repaint()
    }

    private fun updateButtonState(pr: PullRequest?) {
        val ref = pr?.head?.ref
        if (ref == null) {
            ApplicationManager.getApplication().invokeLater {
                checkoutButton.isVisible = false
            }
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val isCurrent = gitUtils.isBranchCurrent(ref)
            val isInSync = if (isCurrent) gitUtils.isCurrentBranchInSync() else true

            ApplicationManager.getApplication().invokeLater {
                if (!isCurrent) {
                    checkoutButton.text = "Checkout and update branch"
                    checkoutButton.isVisible = true
                } else if (!isInSync) {
                    checkoutButton.text = "Update branch"
                    checkoutButton.isVisible = true
                } else {
                    checkoutButton.isVisible = false
                }
                revalidate()
                repaint()
            }
        }
    }

}
