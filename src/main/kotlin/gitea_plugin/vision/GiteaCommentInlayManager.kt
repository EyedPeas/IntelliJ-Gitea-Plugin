package gitea_plugin.vision

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils
import gitea_plugin.GitUtils
import gitea_plugin.GlobalGiteaCache

class GiteaCommentInlayManager : FileEditorManagerListener {
    override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
        val editor = event.newEditor
        if (editor is TextEditor) {
            val file = event.newFile ?: return
            
            // Check if we already have a listener for this editor
            if (editor.editor.getUserData(GITEA_INLAY_LISTENER_KEY) == null) {
                val listener = object : java.awt.event.ComponentAdapter() {
                    override fun componentResized(e: java.awt.event.ComponentEvent?) {
                        updateInlaysAsync(editor.editor, file)
                    }
                }
                editor.editor.component.addComponentListener(listener)
                editor.editor.putUserData(GITEA_INLAY_LISTENER_KEY, listener)
            }
            
            updateInlaysAsync(editor.editor, file)
        }
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val editors = source.getEditors(file)
        for (fileEditor in editors) {
            if (fileEditor is TextEditor) {
                val editor = fileEditor.editor
                
                if (editor.getUserData(GITEA_INLAY_LISTENER_KEY) == null) {
                    val listener = object : java.awt.event.ComponentAdapter() {
                        override fun componentResized(e: java.awt.event.ComponentEvent?) {
                            updateInlaysAsync(editor, file)
                        }
                    }
                    editor.component.addComponentListener(listener)
                    editor.putUserData(GITEA_INLAY_LISTENER_KEY, listener)
                }

                updateInlaysAsync(editor, file)
            }
        }
    }

    companion object {
        private val GITEA_INLAY_LISTENER_KEY = com.intellij.openapi.util.Key.create<java.awt.event.ComponentAdapter>("GITEA_INLAY_LISTENER")
        private val GITEA_TRACKER_REQUESTER_KEY = com.intellij.openapi.util.Key.create<Any>("GITEA_TRACKER_REQUESTER")

        fun updateAllEditors(project: com.intellij.openapi.project.Project) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            
            fileEditorManager.openFiles.forEach { file ->
                fileEditorManager.getEditors(file).forEach { editor ->
                    if (editor is TextEditor) {
                        updateInlaysAsync(editor.editor, file)
                    }
                }
            }
            // Refresh notifications for open files only
            val notifications = com.intellij.ui.EditorNotifications.getInstance(project)
            fileEditorManager.openFiles.forEach { file ->
                notifications.updateNotifications(file)
            }
            
            // Force LineStatusTracker to refresh
            try {
                val manager = LineStatusTrackerManager.getInstanceImpl(project)
                // Use reflections to trigger a full refresh of trackers
                // LineStatusTrackerManager.updateTrackingSettings() is what usually triggers a full refresh when settings change
                val methodNames = listOf("updateTrackingSettings", "resetTrackers", "refreshTrackers")
                for (name in methodNames) {
                    try {
                        val method = manager.javaClass.getDeclaredMethod(name)
                        method.isAccessible = true
                        method.invoke(manager)
                        break // If one succeeds, we are likely done
                    } catch (_: Exception) {
                        // try next
                    }
                }
            } catch (_: Exception) {
                // Ignore
            }
        }

        fun updateInlaysAsync(editor: Editor, file: VirtualFile) {
            val project = editor.project ?: return
            val reviewMode = GlobalGiteaCache.isReviewModeEnabled()

            AppExecutorUtil.getAppExecutorService().execute {
                val gitUtils = GitUtils(project)
                val relativePath = gitUtils.getRepoRelativePath(file) ?: return@execute
                
                if (!reviewMode) {
                    ApplicationManager.getApplication().invokeLater({
                        if (!editor.isDisposed) {
                            updateInlays(editor, relativePath, null)
                        }
                    }, ModalityState.any())
                    return@execute
                }

                val baseBranch = GlobalGiteaCache.getBaseBranch()
                var baseContent: String? = null
                if (baseBranch != null) {
                    try {
                        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file)
                        val root = repository?.root
                        if (root != null) {
                            val bytes = GitFileUtils.getFileContent(project, root, baseBranch, relativePath)
                            baseContent = String(bytes, file.charset)
                        }
                    } catch (_: Exception) {
                        // If file doesn't exist in base branch, treat it as empty (new file)
                        baseContent = ""
                    }
                }
                
                if (!editor.isDisposed) {
                    ApplicationManager.getApplication().invokeLater({
                        updateInlays(editor, relativePath, baseContent)
                    }, ModalityState.defaultModalityState())
                }
            }
        }

        private fun updateInlays(editor: Editor, relativePath: String, baseContent: String?) {
            val commentsByLine = GlobalGiteaCache.getCommentsForFile(relativePath)
            val project = editor.project ?: return
            val reviewMode = GlobalGiteaCache.isReviewModeEnabled()
            val document = editor.document

            // Handle LineStatusTracker using the built-in system with our custom provider
            val manager = LineStatusTrackerManager.getInstanceImpl(project)
            if (!reviewMode) {
                document.getUserData(GITEA_TRACKER_REQUESTER_KEY)?.let {
                    manager.releaseTrackerFor(document, it)
                    document.putUserData(GITEA_TRACKER_REQUESTER_KEY, null)
                }
            } else {
                var requester = document.getUserData(GITEA_TRACKER_REQUESTER_KEY)
                if (requester == null) {
                    requester = Any()
                    manager.requestTrackerFor(document, requester)
                    document.putUserData(GITEA_TRACKER_REQUESTER_KEY, requester)
                }
                
                // If our tracker is active, update its base content
                val tracker = manager.getLineStatusTracker(document)
                if (tracker is SimpleLocalLineStatusTracker && baseContent != null) {
                    tracker.setBaseRevision(baseContent)
                }
            }

            // Clear existing Gitea inlays and components
            val inlaysToDispose = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength)
                .filter { it.renderer is GiteaCommentInlayRenderer }
            
            inlaysToDispose.forEach { inlay ->
                val component = inlay.getUserData(GiteaCommentInlayRenderer.GITEA_INLAY_COMPONENT_KEY)
                if (component != null) {
                    editor.contentComponent.remove(component)
                }
                val positioningListener = inlay.getUserData(GITEA_INLAY_POSITIONING_LISTENER_KEY)
                if (positioningListener != null) {
                    editor.scrollingModel.removeVisibleAreaListener(positioningListener)
                }
                inlay.dispose()
            }

            if (!reviewMode) {
                editor.contentComponent.revalidate()
                editor.contentComponent.repaint()
                return
            }

            if (commentsByLine.isEmpty()) {
                editor.contentComponent.revalidate()
                editor.contentComponent.repaint()
                return
            }

            commentsByLine.forEach { (line, comments) ->
                val lineIndex = line - 1
                if (lineIndex >= 0 && lineIndex < editor.document.lineCount) {
                    val offset = editor.document.getLineEndOffset(lineIndex)
                    
                    val component = GiteaCommentComponent(editor, comments)
                    val renderer = GiteaCommentInlayRenderer(component)
                    val inlay = editor.inlayModel.addBlockElement(offset, true, false, 0, renderer)
                    
                    if (inlay != null) {
                        inlay.putUserData(GiteaCommentInlayRenderer.GITEA_INLAY_COMPONENT_KEY, component)
                        editor.contentComponent.add(component, 0)
                        
                        // Positioning listener
                        val positioningListener = VisibleAreaListener {
                            if (inlay.isValid) {
                                updateComponentBounds(inlay, component)
                            }
                        }
                        editor.scrollingModel.addVisibleAreaListener(positioningListener)
                        inlay.putUserData(GITEA_INLAY_POSITIONING_LISTENER_KEY, positioningListener)
                        
                        updateComponentBounds(inlay, component)
                    }
                }
            }
            editor.contentComponent.revalidate()
            editor.contentComponent.repaint()
        }

        private val GITEA_INLAY_POSITIONING_LISTENER_KEY = com.intellij.openapi.util.Key.create<VisibleAreaListener>("GITEA_INLAY_POSITIONING_LISTENER")

        private fun updateComponentBounds(inlay: com.intellij.openapi.editor.Inlay<*>, component: GiteaCommentComponent) {
            val bounds = inlay.bounds
            if (bounds != null && bounds.width > 0 && bounds.height > 0) {
                component.bounds = bounds
                component.isVisible = true
                component.revalidate()
                component.repaint()
            } else {
                component.isVisible = false
            }
        }
    }
}
