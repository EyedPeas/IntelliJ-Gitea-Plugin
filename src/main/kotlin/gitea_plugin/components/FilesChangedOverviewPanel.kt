package gitea_plugin.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.vcsUtil.VcsUtil
import gitea_plugin.GitUtils
import gitea_plugin.GlobalGiteaCache
import java.awt.BorderLayout
import java.io.File
import javax.swing.tree.DefaultTreeModel

class FilesChangedOverviewPanel(private val project: Project) : JBPanel<FilesChangedOverviewPanel>() {

    private fun getChangesToDisplay(): List<Change> {
        val changedFiles = GlobalGiteaCache.getChangedFiles()
        val repoRootPath = GitUtils(project).getCurrentRepoRoot() ?: project.basePath

        return changedFiles.map { filePath ->
            val normalizedPath = filePath.replace("\\", "/").trim('/')
            val absolutePath = if (repoRootPath != null) {
                val base = repoRootPath.replace("\\", "/").removeSuffix("/")
                "$base/$normalizedPath"
            } else {
                normalizedPath
            }

            val vcsFilePath = VcsUtil.getFilePath(absolutePath, false)
            val virtualFile = vcsFilePath.virtualFile ?: LocalFileSystem.getInstance().findFileByPath(absolutePath)

            val revision = if (virtualFile != null) {
                CurrentContentRevision(vcsFilePath)
            } else {
                object : ContentRevision {
                    override fun getContent(): String? = null
                    override fun getFile(): FilePath = vcsFilePath
                    override fun getRevisionNumber() = com.intellij.openapi.vcs.history.VcsRevisionNumber.NULL
                }
            }
            Change(revision, revision)
        }
    }

    private val tree = object : AsyncChangesTreeImpl<Change>(project, false, false, Change::class.java) {
        override fun buildTreeModel(grouping: ChangesGroupingPolicyFactory, changes: List<Change>): DefaultTreeModel {
            val builder = TreeModelBuilder(project, DirectoryChangesGroupingPolicy.Factory())
            
            val changesToUse = changes.ifEmpty {
                getChangesToDisplay()
            }
            
            builder.setChanges(changesToUse, null)
            return builder.build()
        }

        override fun getChanges(): List<Change> {
            return getChangesToDisplay()
        }
    }

    private val scrollPane = JBScrollPane(tree)

    init {
        layout = BorderLayout()
        add(scrollPane, BorderLayout.CENTER)

        tree.setDoubleClickHandler {
            val selectedNodes = tree.selectionPaths?.map { it.lastPathComponent as? ChangesBrowserNode<*> }
            selectedNodes?.forEach { node ->
                val userObject = node?.userObject
                if (userObject is FilePath) {
                    GitUtils(project).openFile(userObject)
                } else if (userObject is Change) {
                    userObject.afterRevision?.file?.let {
                        GitUtils(project).openFile(it)
                    }
                }
            }
            true
        }

        tree.requestRefresh()

        GlobalGiteaCache.addListener { _ ->
            ApplicationManager.getApplication().invokeLater {
                tree.requestRefresh()
            }
        }

        GlobalGiteaCache.addReviewListener {
            ApplicationManager.getApplication().invokeLater {
                tree.requestRefresh()
            }
        }
    }
}