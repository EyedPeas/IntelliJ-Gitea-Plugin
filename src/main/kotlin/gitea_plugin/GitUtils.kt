package gitea_plugin

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.branch.GitBrancher
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRepositoryManager
import java.io.File

data class GitRepoInfo(val baseUrl: String, val repoOwner: String, val repoName: String)

class GitUtils(val project: Project) {
    private val repositoryManager = GitRepositoryManager.getInstance(project)
    private val repositories get() = repositoryManager.repositories

    fun getRepoBaseUrl(): GitRepoInfo? {
        if (repositories.isEmpty()) return null

        for (repo in repositories) {
            for (remote in repo.info.remotes) {
                for (remotePushUrl in remote.urls) {
                    val sshRegex = Regex("""ssh:\/\/[^@]+@([^:\/]+)(?::\d+)?\/(.+)\/(.+?)(\.git)?$""")
                    val httpsRegex = Regex("""https?:\/\/([^:\/]+)(?::\d+)?\/(.+)\/(.+?)(\.git)?$""")

                    val sshMatch = sshRegex.find(remotePushUrl)
                    if (sshMatch != null) {
                        val host = sshMatch.groupValues[1]
                        val owner = sshMatch.groupValues[2]
                        val name = sshMatch.groupValues[3]
                        return GitRepoInfo("https://$host", owner, name)
                    }

                    val httpsMatch = httpsRegex.find(remotePushUrl)
                    if (httpsMatch != null) {
                        val host = httpsMatch.groupValues[1]
                        val owner = httpsMatch.groupValues[2]
                        val name = httpsMatch.groupValues[3]
                        return GitRepoInfo("https://$host", owner, name)
                    }
                }
            }
        }

        return null
    }

    fun fetchAll() {
        if (repositories.isEmpty()) return
        for (repo in repositories) {
            for (remote in repo.remotes) {
                GitFetchSupport.fetchSupport(project).fetch(repo, remote)
            }
        }
    }

    fun isBranchCurrent(ref: String): Boolean {
        return getCurrentBranch() == ref
    }

    fun isCurrentBranchInSync(): Boolean {
        if (repositories.isEmpty()) return true

        var notInSync = false
        for (repo in repositories) {
            val currentBranch = repo.currentBranch ?: continue
            val trackInfo = repo.getBranchTrackInfo(currentBranch.name) ?: continue
            val remoteBranch = trackInfo.remoteBranch

            val localHash = repo.branches.getHash(currentBranch)
            val remoteHash = repo.branches.getHash(remoteBranch)

            if (localHash != null && remoteHash != null && localHash != remoteHash) {
                notInSync = true
                break
            }
        }
        return !notInSync
    }

    fun updateProject() {
        ApplicationManager.getApplication().invokeLater {
            val actionManager = ActionManager.getInstance()
            val action = actionManager.getAction("Vcs.UpdateProject") ?: return@invokeLater
            val dataContext = DataContext { dataId ->
                if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
            }

            val event = AnActionEvent.createEvent(
                action,
                dataContext,
                null,
                ActionPlaces.UNKNOWN,
                com.intellij.openapi.actionSystem.ActionUiKind.NONE,
                null
            )

            ActionUtil.performAction(action, event)
        }
    }


    fun checkoutBranch(ref: String, onFinished: (() -> Unit)? = null) {
        if (repositories.isEmpty()) {
            onFinished?.invoke()
            return
        }

        val gitBrancher = GitBrancher.getInstance(project)

        gitBrancher.checkout(ref, false, repositories) {
            onFinished?.invoke()
        }
    }

    fun getCurrentRepoRoot(): String? {
        return repositoryManager.repositories.firstOrNull()?.root?.path
    }

    fun getCurrentBranch(): String? {
        val repo = repositories.firstOrNull() ?: return null
        return repo.currentBranch?.name
    }

    fun getRepoRelativePath(virtualFile: VirtualFile): String? {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val root = vcsManager.getVcsRootFor(virtualFile) ?: return null
        
        // Ensure it's a Git repository
        if (vcsManager.getVcsFor(virtualFile)?.name != "Git") return null
        
        val path = VfsUtilCore.getRelativePath(virtualFile, root) ?: return null
        return path.replace("\\", "/").trim('/')
    }

    fun openFile(filePath: FilePath) {
        println("openfile $filePath")
        val virtualFile = filePath.virtualFile ?: LocalFileSystem.getInstance().findFileByPath(filePath.path)
        println("virtualFile ${virtualFile?.path}")
        if (virtualFile != null) {
            val descriptor = OpenFileDescriptor(project, virtualFile)
            descriptor.navigate(true)
        }
    }

    fun openFileAtPosition(path: String, position: Long) {
        if (repositories.isEmpty()) return

        val repoRoot = repositories.first().root.path
        val file = File(repoRoot, path)
        if (file.exists()) {
            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)
            if (virtualFile != null) {
                val descriptor = OpenFileDescriptor(project, virtualFile, position.toInt() - 1, 0)
                descriptor.navigate(true)
            }
        }
    }
}