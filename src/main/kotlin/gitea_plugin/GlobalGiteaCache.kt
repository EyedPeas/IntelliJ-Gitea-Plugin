package gitea_plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.gitea.model.ChangedFile
import io.gitea.model.PullRequest
import io.gitea.model.PullReview
import io.gitea.model.PullReviewComment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object GlobalGiteaCache {
    private var loadedPullRequest: PullRequest? = null
    private val listeners = CopyOnWriteArrayList<(PullRequest?) -> Unit>()
    private val reviewListeners = CopyOnWriteArrayList<() -> Unit>()

    // Map of file path (relative to repo root) to a map of line number to list of comments
    private val fileComments = ConcurrentHashMap<String, MutableMap<Int, MutableList<PullReviewComment>>>()
    private val reviews = ConcurrentHashMap<Long, PullReview>()
    private val reviewComments = ConcurrentHashMap<Long, List<PullReviewComment>>()
    private val changedLines = ConcurrentHashMap<String, Set<Int>>()
    private val changedFiles = mutableListOf<ChangedFile>()
    private var baseBranch: String? = null
    private var shouldShowReviewModeBanner = false
    private var showReviewModeBanner = true

    fun setShowReviewModeBanner(show: Boolean) {
        shouldShowReviewModeBanner = show
    }

    fun shouldShowReviewModeBanner(): Boolean = shouldShowReviewModeBanner

    fun setReviewModeEnabled(enabled: Boolean) {
        showReviewModeBanner = enabled
        notifyReviewListeners()
    }
    fun setChangedFiles(files: List<ChangedFile>) {
        synchronized(changedFiles) {
            changedFiles.clear()
            changedFiles.addAll(files)
        }
        notifyReviewListeners()
    }

    fun getChangedFiles(): List<ChangedFile> {
        synchronized(changedFiles) {
            return changedFiles.toList()
        }
    }

    fun isReviewModeEnabled(): Boolean = showReviewModeBanner

    fun setLoadedPullRequest(pr: PullRequest?, project: Project) {
        clear()
        loadedPullRequest = pr
        if (pr != null) {
            setBaseBranch(pr.base?.ref)
            ApplicationManager.getApplication().executeOnPooledThread {
                loadComments(project, pr)
                loadChangedFiles(project, pr)
            }
        }
        listeners.forEach { it(pr) }
    }

    fun updateComments(project: Project, pr: PullRequest) {
        reviews.clear()
        reviewComments.clear()
        changedLines.clear()
        loadComments(project, pr)
    }

    private fun loadComments(project: Project, pr: PullRequest) {
        val giteaService = GiteaService(project)
        giteaService.fetchPullRequestReviews(
            pr.number,
            onSuccess = { reviewList ->
                setReviews(reviewList)
                reviewList.forEach { review ->
                    if (review.commentsCount > 0) {
                        giteaService.fetchPullReviewComments(pr.number, review.id, onSuccess = { commentList ->
                            setCommentsForReview(review.id, commentList)
                            notifyReviewListeners()
                        }, onError = {})
                    }
                }
                notifyReviewListeners()
            },
            onError = {
                println("Error fetching reviews")
            }
        )

        giteaService.fetchPullRequestDiff(pr.number, onSuccess = { diff ->
            val changedLinesByFile = parseDiff(diff)
            setChangedLines(changedLinesByFile)
            notifyReviewListeners()
        }, onError = {
            println("Error fetching PR diff")
        })
    }

    private fun loadChangedFiles(project: Project, pr: PullRequest) {
        val giteaService = GiteaService(project)
        giteaService.fetchAllChangedFiles(pr.number, onSuccess =  { changedFiles ->
            setChangedFiles(changedFiles)
        }, onError = {
            println("Error fetching changed files")
        })
    }

    private fun parseDiff(diff: String): Map<String, Set<Int>> {
        val result = mutableMapOf<String, MutableSet<Int>>()
        var currentFile: String? = null
        var currentLineInNewFile = 0

        diff.lines().forEach { line ->
            if (line.startsWith("+++ ")) {
                currentFile = line.substring(4).removePrefix("b/").trim()
            } else if (line.startsWith("@@ ")) {
                // @@ -line,count +line,count @@
                val match = Regex("""@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@""").find(line)
                if (match != null) {
                    currentLineInNewFile = match.groupValues[1].toInt()
                }
            } else if (currentFile != null && !line.startsWith("--- ")) {
                if (line.startsWith("+")) {
                    result.getOrPut(currentFile) { mutableSetOf() }.add(currentLineInNewFile)
                    currentLineInNewFile++
                } else if (line.startsWith("-")) {
                    // Skip deletions in new file numbering
                } else if (line.isNotEmpty()) {
                    currentLineInNewFile++
                }
            }
        }
        return result
    }

    fun getLoadedPullRequest(): PullRequest? = loadedPullRequest

    fun addListener(listener: (PullRequest?) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (PullRequest?) -> Unit) {
        listeners.remove(listener)
    }

    fun addReviewListener(listener: () -> Unit) {
        reviewListeners.add(listener)
    }

    fun removeReviewListener(listener: () -> Unit) {
        reviewListeners.remove(listener)
    }

    private fun notifyReviewListeners() {
        reviewListeners.forEach { it() }
    }

    fun setChangedLines(filesChangedLines: Map<String, Set<Int>>) {
        changedLines.clear()
        filesChangedLines.forEach { (path, lines) ->
            val normalizedPath = path.replace("\\", "/").trim('/')
            changedLines[normalizedPath] = lines
        }
        setShowReviewModeBanner(true)
    }

    fun getChangedLinesForFile(path: String): Set<Int> {
        val normalizedPath = path.replace("\\", "/").trim('/')
        return changedLines[normalizedPath] ?: emptySet()
    }

    fun setBaseBranch(branch: String?) {
        baseBranch = branch
    }

    fun getBaseBranch(): String? = baseBranch

    fun setReviews(reviewList: List<PullReview>) {
        reviewList.forEach { reviews[it.id] = it }
    }

    fun getReviews(): List<PullReview> = reviews.values.toList()

    fun setCommentsForReview(reviewId: Long, comments: List<PullReviewComment>) {
        reviewComments[reviewId] = comments
        comments.groupBy { it.path }.forEach { (path, comments) ->
            if (path != null) {
                setCommentsForFile(path, comments)
            }
        }
    }

    fun getCommentsForReview(reviewId: Long): List<PullReviewComment> = reviewComments[reviewId] ?: emptyList()

    fun hasPullRequestLoaded(): Boolean = reviews.isNotEmpty()

    fun getReview(id: Long): PullReview? = reviews[id]

    fun setCommentsForFile(path: String, comments: List<PullReviewComment>) {
        val normalizedPath = path.replace("\\", "/").trim('/')
        val lineMap = fileComments.getOrPut(normalizedPath) { ConcurrentHashMap() }
        comments.filter { it.position != null || it.originalPosition != null }.groupBy {
            (it.position ?: it.originalPosition)!!
        }.forEach { (line, newComments) ->
            val existingComments = lineMap.getOrPut(line) { mutableListOf() }
            // Avoid duplicates if same review is fetched again
            newComments.forEach { newComment ->
                if (existingComments.none { it.id == newComment.id }) {
                    existingComments.add(newComment)
                }
            }
        }
    }

    fun getCommentsForFile(path: String): Map<Int, List<PullReviewComment>> {
        val normalizedPath = path.replace("\\", "/").trim('/')
        return fileComments[normalizedPath] ?: emptyMap()
    }

    fun clear() {
        fileComments.clear()
        reviews.clear()
        reviewComments.clear()
        changedLines.clear()
        synchronized(changedFiles) {
            changedFiles.clear()
        }
        baseBranch = null
    }
}
