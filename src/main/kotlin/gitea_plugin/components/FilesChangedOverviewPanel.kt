package gitea_plugin.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import gitea_plugin.GitUtils
import gitea_plugin.GlobalGiteaCache
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class FilesChangedOverviewPanel(project: Project) : JBPanel<FilesChangedOverviewPanel>(){
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("root"))
    private val changedFilesTree = Tree(treeModel)
    private val scrollPane = JBScrollPane(changedFilesTree)

    init {
        layout = BorderLayout()
        changedFilesTree.isRootVisible = false
        add(scrollPane, BorderLayout.CENTER)
        updateChangedFiles()

        changedFilesTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path: TreePath? = changedFilesTree.getPathForLocation(e.x, e.y)
                    val node = path?.lastPathComponent as? DefaultMutableTreeNode
                    if (node != null && node.isLeaf) {
                        val filePath = getFullFilePath(node)
                        GitUtils(project).openFileAtPosition(filePath, 1)
                    }
                }
            }
        })

        GlobalGiteaCache.addListener { pr ->
            ApplicationManager.getApplication().invokeLater {
                updateChangedFiles()
            }
        }

        GlobalGiteaCache.addReviewListener {
            ApplicationManager.getApplication().invokeLater {
                updateChangedFiles()
            }
        }
    }

    private fun getFullFilePath(node: DefaultMutableTreeNode): String {
        val pathObjects = node.userObjectPath
        // Skip the root node if it's invisible but present in the path
        val components = pathObjects.filter { it.toString() != "root" }.map { it.toString() }
        return components.joinToString("/")
    }

    private fun updateChangedFiles() {
        val changedFiles = GlobalGiteaCache.getChangedFiles()
        val root = DefaultMutableTreeNode("root")

        changedFiles.sorted().forEach { filePath ->
            val parts = filePath.split("/")
            var currentNode = root
            for (part in parts) {
                var found = false
                for (i in 0 until currentNode.childCount) {
                    val child = currentNode.getChildAt(i) as DefaultMutableTreeNode
                    if (child.userObject == part) {
                        currentNode = child
                        found = true
                        break
                    }
                }
                if (!found) {
                    val newNode = DefaultMutableTreeNode(part)
                    currentNode.add(newNode)
                    currentNode = newNode
                }
            }
        }

        val collapsedRoot = collapseSingleChildNodes(root)
        treeModel.setRoot(collapsedRoot)
        expandAllNodes(changedFilesTree, 0, changedFilesTree.rowCount)
    }

    private fun collapseSingleChildNodes(node: DefaultMutableTreeNode): DefaultMutableTreeNode {
        if (node.isLeaf) return node

        // Recursively collapse children first
        val children = (0 until node.childCount).map { node.getChildAt(it) as DefaultMutableTreeNode }
        node.removeAllChildren()
        children.forEach { child ->
            node.add(collapseSingleChildNodes(child))
        }

        // Check if we should collapse this node with its only child
        if (node.childCount == 1) {
            val child = node.getChildAt(0) as DefaultMutableTreeNode
            // Collapse if:
            // 1. Current node is not root AND child is not a leaf
            // 2. Current node is root AND child is not a leaf (to collapse top-level single-child directories)
            if (!child.isLeaf) {
                val newNode = if (node.userObject == "root") {
                    DefaultMutableTreeNode(child.userObject)
                } else {
                    DefaultMutableTreeNode("${node.userObject}/${child.userObject}")
                }
                val grandChildren = (0 until child.childCount).map { child.getChildAt(it) as DefaultMutableTreeNode }
                grandChildren.forEach { newNode.add(it) }
                // Re-evaluate the new node for further collapsing
                return collapseSingleChildNodes(newNode)
            }
        }

        return node
    }

    private fun expandAllNodes(tree: Tree, startingIndex: Int, rowCount: Int) {
        for (i in startingIndex until rowCount) {
            tree.expandRow(i)
        }

        if (tree.rowCount != rowCount) {
            expandAllNodes(tree, rowCount, tree.rowCount)
        }
    }
}