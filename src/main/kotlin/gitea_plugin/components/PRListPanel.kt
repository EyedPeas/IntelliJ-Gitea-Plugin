package gitea_plugin.components

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import io.gitea.model.PullRequest
import javax.swing.BorderFactory
import javax.swing.DefaultListModel

class PRListPanel(private val onSelectionChanged: (PullRequest?) -> Unit) : JBScrollPane() {
    private val prListModel = DefaultListModel<String>()
    private val prList = JBList(prListModel)
    private val prs = mutableListOf<PullRequest>()

    init {
        border = BorderFactory.createTitledBorder("Pull Requests")
        setViewportView(prList)
        setupListSelectionListener()
    }

    private fun setupListSelectionListener() {
        prList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val selectedPR = prs.getOrNull(prList.selectedIndex)
                onSelectionChanged(selectedPR)
            }
        }
    }

    fun updateList(newPrs: List<PullRequest>) {
        prs.clear()
        prs.addAll(newPrs)
        prListModel.clear()
        if (newPrs.isEmpty()) {
            prListModel.addElement("No open pull requests found")
        } else {
            newPrs.forEach { prListModel.addElement("${it.state} | ${it.title}") }
        }
    }

    fun showMessage(message: String) {
        prs.clear()
        prListModel.clear()
        prListModel.addElement(message)
    }

    fun clear() {
        prs.clear()
        prListModel.clear()
    }
}
