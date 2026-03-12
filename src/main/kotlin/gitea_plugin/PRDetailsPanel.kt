package gitea_plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import io.gitea.model.PullRequest
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
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

        table.columnModel.getColumn(1).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                val propertyName = table.model.getValueAt(row, 0) as String
                if (propertyName == "Branch") {
                    val currentBranch = GlobalGiteaCache.getLoadedPullRequest()?.head?.ref
                    if (currentBranch == value) {
                        component.foreground = JBColor.GREEN
                    } else {
                        component.foreground = JBColor.BLUE
                    }
                    val font = component.font
                    val attributes = font.attributes.toMutableMap()
                    attributes[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
                    component.font = font.deriveFont(attributes)
                } else {
                    component.foreground = table.foreground
                    component.font = table.font
                }
                return component
            }
        }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val column = table.columnAtPoint(e.point)
                if (row != -1 && column == 1) {
                    val propertyName = tableModel.getValueAt(row, 0) as String
                    if (propertyName == "Branch") {
                        val branchName = tableModel.getValueAt(row, 1) as String
                        ApplicationManager.getApplication().executeOnPooledThread {
                            gitUtils.checkoutBranch(branchName)
                            ApplicationManager.getApplication().invokeLater {
                                table.repaint()
                            }
                        }
                    }
                }
            }
        })
        table.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val column = table.columnAtPoint(e.point)
                if (row != -1 && column == 1) {
                    val propertyName = tableModel.getValueAt(row, 0) as String
                    if (propertyName == "Branch") {
                        table.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        table.toolTipText = "Check out branch if clicked"
                        return
                    }
                }
                table.cursor = Cursor.getDefaultCursor()
                table.toolTipText = null
            }
        })
    }

    fun updateDetails(selectedPR: PullRequest?) {
        currentPR = selectedPR

        pane.border = BorderFactory.createTitledBorder("Details: ${selectedPR?.title ?: "No PR selected"}")
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
