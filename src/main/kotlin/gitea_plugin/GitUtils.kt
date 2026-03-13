package gitea_plugin

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
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

    fun updateCurrentBranch() {
        if (repositories.isEmpty()) return

        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction("Vcs.UpdateProject") ?: return
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

    fun checkoutBranch(ref: String) {
        if (repositories.isEmpty()) return

        val gitBrancher = GitBrancher.getInstance(project)

        gitBrancher.checkout(ref, false, repositories) {
        }
    }

    fun getCurrentBranch(): String? {
        if (repositories.isEmpty()) return null
        return repositories.firstOrNull()?.currentBranch?.name
    }

    fun getRepoRelativePath(virtualFile: com.intellij.openapi.vfs.VirtualFile): String? {
        val repo = repositoryManager.getRepositoryForFile(virtualFile) ?: return null
        val path = com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(virtualFile, repo.root) ?: return null
        return path.replace("\\", "/").trim('/')
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