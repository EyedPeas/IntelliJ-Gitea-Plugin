package gitea_plugin.vision

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import gitea_plugin.GlobalGiteaCache
import kotlinx.html.Entities
import java.util.function.Function
import javax.swing.JComponent

class GiteaReviewNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
//        var isPullRequestLoaded = false
//        GlobalGiteaCache.addListener { pr ->
//            println("[GiteaReviewNotificationProvider] Pull request loaded: $isPullRequestLoaded")
//            isPullRequestLoaded = GlobalGiteaCache.hasPullRequestLoaded()
//        }
//
//        if (!isPullRequestLoaded) {
//            println("[GiteaReviewNotificationProvider] No pull request loaded: returning null")
//            return null
//        }
//        if (!GlobalGiteaCache.shouldShowReviewModeBanner()) {
//            println("[GiteaReviewNotificationProvider] Review mode banner disabled: returning null")
//            return null
//        }

        return Function { fileEditor ->
            if (fileEditor !is TextEditor) return@Function null


            if (!GlobalGiteaCache.hasPullRequestLoaded()) return@Function null

            val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
            panel.text = "Gitea Review Mode"

            if (GlobalGiteaCache.isReviewModeEnabled()) {
                panel.createActionLabel("Disable Review Mode") {
                    GlobalGiteaCache.setReviewModeEnabled(false)
                    updateAllEditors(project)
                }
            } else {
                panel.createActionLabel("Enable Review Mode") {
                    GlobalGiteaCache.setReviewModeEnabled(true)
                    updateAllEditors(project)
                }
            }

            panel
        }
    }

    private fun updateAllEditors(project: Project) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.forEach { file ->
            fileEditorManager.getEditors(file).forEach { editor ->
                if (editor is TextEditor) {
                    GiteaCommentInlayManager.updateInlaysAsync(editor.editor, file)
                }
            }
        }
        // Refresh notifications
        EditorNotifications.getInstance(project).updateAllNotifications()
    }
}
