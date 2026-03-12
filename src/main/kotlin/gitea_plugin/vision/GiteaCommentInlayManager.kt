package gitea_plugin.vision

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.AppExecutorUtil
import gitea_plugin.GitUtils
import gitea_plugin.GlobalGiteaCache
import java.awt.*
import java.awt.event.MouseEvent
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.EditorGutterAction
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils

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

        fun updateInlaysAsync(editor: Editor, file: VirtualFile) {
            val project = editor.project ?: return
            AppExecutorUtil.getAppExecutorService().execute {
                val gitUtils = GitUtils(project)
                val relativePath = gitUtils.getRepoRelativePath(file)
                
                if (relativePath != null && !editor.isDisposed) {
                    ApplicationManager.getApplication().invokeLater({
                        updateInlays(editor, file, relativePath)
                    }, ModalityState.defaultModalityState())
                }
            }
        }

        private fun updateInlays(editor: Editor, file: VirtualFile, relativePath: String) {
            val commentsByLine = GlobalGiteaCache.getCommentsForFile(relativePath)
            val changedLines = GlobalGiteaCache.getChangedLinesForFile(relativePath)
            val reviewMode = GlobalGiteaCache.isReviewModeEnabled()

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

            // Clear existing gutter highlighters
            val highlightersToRemove = editor.markupModel.allHighlighters
                .filter { it.getUserData(GITEA_GUTTER_MARKER_KEY) == true }
            highlightersToRemove.forEach { editor.markupModel.removeHighlighter(it) }

            if (!reviewMode) {
                editor.contentComponent.revalidate()
                editor.contentComponent.repaint()
                return
            }

            // Add gutter markers for changed lines
            changedLines.forEach { line ->
                val lineIndex = line - 1
                if (lineIndex >= 0 && lineIndex < editor.document.lineCount) {
                    val highlighter = editor.markupModel.addLineHighlighter(lineIndex, HighlighterLayer.LAST, null)
                    highlighter.putUserData(GITEA_GUTTER_MARKER_KEY, true)
                    highlighter.errorStripeTooltip = "This line has been changed"
                    highlighter.setErrorStripeMarkColor(JBColor.CYAN)
                    highlighter.lineMarkerRenderer = object : ActiveGutterRenderer, EditorGutterAction {
                        override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
                            g.color = JBColor.CYAN
//                            g.color = JBColor(Color(0, 200, 0, 180), Color(0, 150, 0, 180)) // Slightly more opaque green
                            // Drawing a 4-pixel wide bar at the left edge of the gutter rectangle
                            g.fillRect(r.x, r.y, 4, r.height)
                        }

                        override fun canDoAction(editor: Editor, e: MouseEvent): Boolean = true

                        override fun doAction(editor: Editor, e: MouseEvent) {
                            showDiff(editor, file)
                        }

                        override fun doAction(lineNum: Int) {
                            showDiff(editor, file)
                        }

                        override fun getCursor(lineNum: Int): Cursor? {
                            return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        }
                    }
                }
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
        private val GITEA_GUTTER_MARKER_KEY = com.intellij.openapi.util.Key.create<Boolean>("GITEA_GUTTER_MARKER")

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

        private fun showDiff(editor: Editor, file: VirtualFile) {
            val project = editor.project ?: return
            val baseBranch = GlobalGiteaCache.getBaseBranch() ?: "master"

            ApplicationManager.getApplication().executeOnPooledThread {
                val relativePath = GitUtils(project).getRepoRelativePath(file) ?: return@executeOnPooledThread
                val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file) ?: return@executeOnPooledThread

                try {
                    val baseContentBytes = GitFileUtils.getFileContent(project, repository.root, baseBranch, relativePath)
                    val baseContent = String(baseContentBytes, file.charset)
                    
                    ApplicationManager.getApplication().invokeLater {
                        if (editor.isDisposed) return@invokeLater
                        val factory = DiffContentFactory.getInstance()
                        val content1 = factory.create(project, baseContent, file.fileType)
                        val content2 = factory.create(project, file)
                        
                        val request = SimpleDiffRequest("Diff: $relativePath ($baseBranch vs Current)", content1, content2, baseBranch, "Current")
                        DiffManager.getInstance().showDiff(project, request)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
