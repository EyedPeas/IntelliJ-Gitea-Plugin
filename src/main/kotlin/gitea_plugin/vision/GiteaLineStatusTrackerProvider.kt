package gitea_plugin.vision

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LocalLineStatusTrackerProvider
import com.intellij.openapi.vfs.VirtualFile
import gitea_plugin.GitUtils
import gitea_plugin.GlobalGiteaCache

class GiteaLineStatusTrackerProvider : LocalLineStatusTrackerProvider {
    override fun isTrackedFile(project: Project, file: VirtualFile): Boolean {
        if (!GlobalGiteaCache.isReviewModeEnabled()) return false
        val gitUtils = GitUtils(project)
        val relativePath = gitUtils.getRepoRelativePath(file) ?: return false
        
        // Return true if the file has any changes in the PR or any comments (to show review context)
        // Note: GlobalGiteaCache.getChangedLinesForFile and getCommentsForFile already normalize the path internally.
        return GlobalGiteaCache.getChangedLinesForFile(relativePath).isNotEmpty() || 
               GlobalGiteaCache.getCommentsForFile(relativePath).isNotEmpty()
    }

    override fun isMyTracker(tracker: LocalLineStatusTracker<*>): Boolean {
        return tracker is SimpleLocalLineStatusTracker
    }

    override fun createTracker(project: Project, file: VirtualFile): LocalLineStatusTracker<*>? {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        return SimpleLocalLineStatusTracker(project, document, file)
    }
}
