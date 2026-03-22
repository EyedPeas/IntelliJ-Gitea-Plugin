package gitea_plugin.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import gitea_plugin.GitUtils
import gitea_plugin.GlobalGiteaCache
import gitea_plugin.MyMessageBundle
import java.awt.BorderLayout
import javax.swing.tree.DefaultTreeModel

class ChangedFilesOverviewPanel(private val project: Project) : JBPanel<ChangedFilesOverviewPanel>() {

    private fun getChangesToDisplay(): List<Change> {
        val changedFiles = GlobalGiteaCache.getChangedFiles()
        val repoRootPath = GitUtils(project).getCurrentRepoRoot() ?: project.basePath
        val baseBranch = GlobalGiteaCache.getBaseBranch() ?: "master"

        return changedFiles.map { changedFile ->
            val filename = changedFile.filename
            val normalizedPath = filename.replace("\\", "/").trim('/')
            val absolutePath = if (repoRootPath != null) {
                val base = repoRootPath.replace("\\", "/").removeSuffix("/")
                "$base/$normalizedPath"
            } else {
                normalizedPath
            }

            val vcsFilePath = VcsUtil.getFilePath(absolutePath, false)
            val baseRevisionNumber = GitRevisionNumber(baseBranch)

            val beforeRevision: ContentRevision?
            val afterRevision: ContentRevision?

            when (changedFile.status) {
                "added" -> {
                    beforeRevision = null
                    afterRevision = CurrentContentRevision(vcsFilePath)
                }

                "deleted" -> {
                    beforeRevision = GitContentRevision.createRevision(vcsFilePath, baseRevisionNumber, project)
                    afterRevision = null
                }

                "renamed" -> {
                    val previousPath = changedFile.previousFilename
                    val normalizedPreviousPath = previousPath.replace("\\", "/").trim('/')
                    val absolutePreviousPath = if (repoRootPath != null) {
                        val base = repoRootPath.replace("\\", "/").removeSuffix("/")
                        "$base/$normalizedPreviousPath"
                    } else {
                        normalizedPreviousPath
                    }
                    val previousVcsFilePath = VcsUtil.getFilePath(absolutePreviousPath, false)

                    beforeRevision = GitContentRevision.createRevision(previousVcsFilePath, baseRevisionNumber, project)
                    afterRevision = CurrentContentRevision(vcsFilePath)
                }

                else -> {
                    // modified or default
                    beforeRevision = GitContentRevision.createRevision(vcsFilePath, baseRevisionNumber, project)
                    afterRevision = CurrentContentRevision(vcsFilePath)
                }
            }

            Change(beforeRevision, afterRevision)
        }
    }

    private fun createDummyRevision(vcsFilePath: FilePath): ContentRevision {
        return object : ContentRevision {
            override fun getContent(): String? = null
            override fun getFile(): FilePath = vcsFilePath
            override fun getRevisionNumber() = com.intellij.openapi.vcs.history.VcsRevisionNumber.NULL
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
            val pr = GlobalGiteaCache.getLoadedPullRequest()
            val headRef = pr?.head?.ref
            if (headRef != null && !GitUtils(project).isBranchCurrent(headRef)) {
                val result = MessageDialogBuilder.yesNo(
                    MyMessageBundle.message("notification.branch.mismatch.title"),
                    MyMessageBundle.message("notification.branch.mismatch.message", headRef)
                ).yesText("Checkout and update branch").noText("Cancel").ask(project)

                if (result) {
                    GitUtils(project).checkoutBranch(headRef) {
                        GitUtils(project).updateProject()
                    }
                }

                return@setDoubleClickHandler true
            }

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

        var isUpdatingListener = java.util.concurrent.atomic.AtomicBoolean(false)
        GlobalGiteaCache.addListener { _ ->
            if (isUpdatingListener.getAndSet(true)) return@addListener
            ApplicationManager.getApplication().invokeLater {
                isUpdatingListener.set(false)
                tree.requestRefresh()
            }
        }

        var isUpdatingReviewListener = java.util.concurrent.atomic.AtomicBoolean(false)
        GlobalGiteaCache.addReviewListener {
            if (isUpdatingReviewListener.getAndSet(true)) return@addReviewListener
            ApplicationManager.getApplication().invokeLater {
                isUpdatingReviewListener.set(false)
                tree.requestRefresh()
            }
        }
    }
}