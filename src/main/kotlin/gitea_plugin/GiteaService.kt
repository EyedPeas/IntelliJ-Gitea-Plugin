package gitea_plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.gitea.Configuration
import io.gitea.api.RepositoryApi
import io.gitea.api.UserApi
import io.gitea.auth.ApiKeyAuth
import io.gitea.model.ChangedFile
import io.gitea.model.PullRequest
import io.gitea.model.PullReview
import io.gitea.model.PullReviewComment

class GiteaService(private val project: Project) {
    private val apiClient = Configuration.getDefaultApiClient()
    private var repoOwner: String = ""
    private var repoName: String = ""
    private var gitRepoInfo: GitRepoInfo? = null

    init {
        // Initialization is done lazily via ensureReady() to avoid calling GitUtils on EDT during service creation
    }

    private fun ensureReady() {
        if (gitRepoInfo == null) {
            gitRepoInfo = GitUtils(project).getRepoBaseUrl()
            gitRepoInfo?.let {
                repoOwner = it.repoOwner
                repoName = it.repoName
            }
        }
        gitRepoInfo?.let {
            configureApiClient(it)
        }
    }

    private fun configureApiClient(info: GitRepoInfo) {
        apiClient.setBasePath("${info.baseUrl}/api/v1")
        val certificateManager = com.intellij.util.net.ssl.CertificateManager.getInstance()
        apiClient.getHttpClient().setSslSocketFactory(certificateManager.sslContext.socketFactory)

        val accessToken = apiClient.getAuthentication("AuthorizationHeaderToken") as ApiKeyAuth
        accessToken.apiKey = GiteaSettings.getInstance().state.giteaToken
        accessToken.apiKeyPrefix = "token"
    }

    fun isReady(): Boolean = GitUtils(project).getRepoBaseUrl() != null

    fun fetchUserData(onSuccess: (String) -> Unit, onError: () -> Unit) {
        ensureReady()
        if (!isReady()) {
            onError()
            return
        }
        val userApi = UserApi()
        try {
            val user = userApi.userGetCurrent()
            onSuccess(user.email)
        } catch (e: Exception) {
            e.printStackTrace()
            onError()
        }
    }

    fun fetchPullRequests(onSuccess: (List<PullRequest>) -> Unit, onError: () -> Unit) {
        ensureReady()
        if (!isReady()) {
            onError()
            return
        }
        val repoApi = RepositoryApi()
        try {
            val results = repoApi.repoListPullRequests(repoOwner, repoName, "open", null, null, null, null, null)
            onSuccess(results)
        } catch (e: Exception) {
            e.printStackTrace()
            onError()
        }
    }

    fun fetchPullRequestReviews(pullRequestIndex: Long, onSuccess: (List<PullReview>) -> Unit, onError: () -> Unit) {
        ensureReady()
        if (!isReady()) {
            onError()
            return
        }
        val repoApi = RepositoryApi()
        try {
            val results = repoApi.repoListPullReviews(repoOwner, repoName, pullRequestIndex, null, null)
            onSuccess(results)
        } catch (e: Exception) {
            e.printStackTrace()
            onError()
        }
    }

    fun fetchPullReviewComments(pullRequestIndex: Long, reviewIndex: Long, onSuccess: (List<PullReviewComment>) -> Unit, onError: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            ensureReady()
            if (!isReady()) {
                onError()
                return@executeOnPooledThread
            }
            val repoApi = RepositoryApi()
            try {
                val results = repoApi.repoGetPullReviewComments(repoOwner, repoName, pullRequestIndex, reviewIndex)
                ApplicationManager.getApplication().invokeLater {
                    onSuccess(results)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ApplicationManager.getApplication().invokeLater {
                    onError()
                }
            }
        }
    }

    fun fetchPullRequestDiff(pullRequestIndex: Long, onSuccess: (String) -> Unit, onError: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            ensureReady()
            if (!isReady()) {
                onError()
                return@executeOnPooledThread
            }
            val repoApi = RepositoryApi()
            try {
                // repoDownloadPullDiffOrPatch(owner, repo, index, diff/patch, binary)
                val diff = repoApi.repoDownloadPullDiffOrPatch(repoOwner, repoName, pullRequestIndex, "diff", false)
                ApplicationManager.getApplication().invokeLater {
                    onSuccess(diff)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ApplicationManager.getApplication().invokeLater {
                    onError()
                }
            }
        }
    }

    fun fetchAllChangedFiles(pullRequestIndex: Long, onSuccess: (List<ChangedFile>) -> Unit, onError: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            ensureReady()
            if (!isReady()) {
                onError()
                return@executeOnPooledThread
            }
            val repoApi = RepositoryApi()
            try {
                val changedFiles = repoApi.repoGetPullRequestFiles(repoOwner, repoName, pullRequestIndex, null, null, null, null)
                ApplicationManager.getApplication().invokeLater {
                    onSuccess(changedFiles)
                }
            } catch (_: Exception) {
                onError()
            }
        }
    }
}
