package gitea_plugin.vision

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import gitea_plugin.GlobalGiteaCache
import java.util.function.Function
import javax.swing.JComponent

class GiteaReviewNotificationProvider(private val project: Project) : EditorNotificationProvider {

    init {
        val isUpdating = java.util.concurrent.atomic.AtomicBoolean(false)
        GlobalGiteaCache.addReviewListener {
            if (isUpdating.getAndSet(true)) return@addReviewListener
            ApplicationManager.getApplication().invokeLater({
                isUpdating.set(false)
                if (!project.isDisposed) {
                    GiteaCommentInlayManager.updateAllEditors(project)
                }
            }, ModalityState.any())
        }
    }

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        return Function { fileEditor ->
            if (fileEditor !is TextEditor) return@Function null


            if (!GlobalGiteaCache.hasPullRequestLoaded()) return@Function null

            val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
            panel.text = "Gitea Review Mode"

            if (GlobalGiteaCache.isReviewModeEnabled()) {
                panel.createActionLabel("Disable Review Mode") {
                    GlobalGiteaCache.setReviewModeEnabled(false)
                }
            } else {
                panel.createActionLabel("Enable Review Mode") {
                    GlobalGiteaCache.setReviewModeEnabled(true)
                }
            }

            panel
        }
    }

}
