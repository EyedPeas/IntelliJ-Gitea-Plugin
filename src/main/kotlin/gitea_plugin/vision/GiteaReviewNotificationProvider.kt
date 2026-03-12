package gitea_plugin.vision

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import gitea_plugin.GlobalGiteaCache
import java.util.function.Function
import javax.swing.JComponent

class GiteaReviewNotificationProvider : EditorNotificationProvider {
    private var showNotificationBanner = false
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
//        // Only show if we have some data for this file or review mode is enabled
//        val hasComments = GlobalGiteaCache.getCommentsForFile(file.path).isNotEmpty() ||
//                         GlobalGiteaCache.getChangedLinesForFile(file.path).isNotEmpty()
//
//        if (!hasComments && !GlobalGiteaCache.isReviewModeEnabled()) return null

        if (!GlobalGiteaCache.shouldShowReviewModeBanner()) return null

        return Function { fileEditor ->
            if (fileEditor !is TextEditor) return@Function null

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
        com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications()
    }
}
