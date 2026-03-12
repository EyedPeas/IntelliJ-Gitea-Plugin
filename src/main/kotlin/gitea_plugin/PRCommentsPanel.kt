package gitea_plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.UIUtil
import gitea_plugin.vision.GiteaCommentInlayManager
import io.gitea.model.PullReview
import io.gitea.model.PullReviewComment
import org.threeten.bp.format.DateTimeFormatter
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.BoxLayout.Y_AXIS

class PRCommentsPanel(private val project: Project) : JBPanel<PRCommentsPanel>(BorderLayout()) {
    private val gitUtils = GitUtils(project)
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private val contentPanel = ScrollablePanel().apply {
        layout = BoxLayout(this, Y_AXIS)
        alignmentX = LEFT_ALIGNMENT
    }

    init {
        add(JBScrollPane(contentPanel), BorderLayout.CENTER)
        contentPanel.add(JBLabel("No Pull Request selected").apply {
            alignmentX = CENTER_ALIGNMENT
            alignmentY = CENTER_ALIGNMENT
            foreground = UIUtil.getInactiveTextColor()
        })

        GlobalGiteaCache.addListener { pr ->
            ApplicationManager.getApplication().invokeLater {
                if (pr != null) {
                    showLoadingText()
                } else {
                    refreshComments()
                }
            }
        }

        GlobalGiteaCache.addReviewListener {
            ApplicationManager.getApplication().invokeLater {
                refreshComments()
                triggerCodeVisionUpdate()
            }
        }
    }

    private class ScrollablePanel : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: java.awt.Rectangle?, orientation: Int, direction: Int): Int = 10
        override fun getScrollableBlockIncrement(visibleRect: java.awt.Rectangle?, orientation: Int, direction: Int): Int = 100
        override fun getScrollableTracksViewportWidth(): Boolean = true
        override fun getScrollableTracksViewportHeight(): Boolean = false
    }

    private fun createVerticalPanel() = JPanel().apply {
        layout = BoxLayout(this, Y_AXIS)
        alignmentX = LEFT_ALIGNMENT
    }

    fun refreshComments() {
        val pr = GlobalGiteaCache.getLoadedPullRequest()
        val reviews = GlobalGiteaCache.getReviews()
        contentPanel.removeAll()
        if (pr == null) {
            contentPanel.add(JBLabel("No Pull Request selected").apply {
                alignmentX = CENTER_ALIGNMENT
                alignmentY = CENTER_ALIGNMENT
                foreground = UIUtil.getInactiveTextColor()
            })
            revalidate()
            repaint()
            return
        }

        if (reviews.isEmpty()) {
            contentPanel.add(JBLabel("No comments found.").apply {
                alignmentY = CENTER_ALIGNMENT
                alignmentX = CENTER_ALIGNMENT
                foreground = UIUtil.getInactiveTextColor()
            })
        } else {
            reviews.sortedBy { it.submittedAt }.forEach { review ->
                val author = review.user?.login ?: "Unknown"
                var reviewHeaderText = when (review.state) {
                    "APPROVED" -> "<html>$author <b>approved</b>"
                    "REQUEST_CHANGES" -> "<html>$author <b>requested changes</b>"
                    "COMMENT" -> "<html>$author <b>reviewed</b>"
                    "REQUEST_REVIEW" -> "<html>$author <b>requested review</b>"
                    else -> "<html>$author"
                }
                reviewHeaderText += " at ${review.submittedAt?.format(formatter)}</html>"

                val comments = GlobalGiteaCache.getCommentsForReview(review.id)
                if (comments.isNotEmpty()) {
                    val reviewPanel = createVerticalPanel()
                    reviewPanel.border = BorderFactory.createTitledBorder(reviewHeaderText)

                    addComments(review, comments, reviewPanel)

                    contentPanel.add(reviewPanel)
                } else {
                    contentPanel.add(JBLabel(reviewHeaderText).apply {
                        alignmentX = LEFT_ALIGNMENT
                    })
                }
            }
        }
        revalidate()
        repaint()
    }

    private fun addComments(review: PullReview, commentList: List<PullReviewComment>, reviewPanel: JPanel) {
        commentList.filter { it.path != null && (it.position != null || it.originalPosition != null) }
            .groupBy { (it.path!!.replace("\\", "/").trim('/')) to (it.position ?: it.originalPosition) }
            .forEach { (threadKey, threadComments) ->
                val (path, position) = threadKey
                val safePosition = position ?: 0

                val threadPanel = createVerticalPanel().apply {
                    val titleLabel = LinkLabel<Unit>("Thread: $path#$safePosition", null) { _, _ ->
                        if (path != "" && position != null) {
                            gitUtils.openFileAtPosition(path, position.toLong())
                        }
                    }
                    add(titleLabel)
                    border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                }

                val diffText = threadComments.first().diffHunk ?: ""
                val htmlDiff = diffText.lines().joinToString("") { line ->
                    val escapedLine = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    when {
                        line.startsWith("+") -> "<div style='background-color: #808080; color: #22CB3C;'>$escapedLine</div>"
                        line.startsWith("-") -> "<div style='background-color: #808080; color: #cb2431;'>$escapedLine</div>"
                        line.startsWith("@@") -> "<div style='background-color: #808080; color: #005cc5;'>$escapedLine</div>"
                        else -> "<div>$escapedLine</div>"
                    }
                }
                val diffPane = JTextPane().apply {
                    contentType = "text/html"
                    text =
                        "<html><body style='color: black; font-family: monospace; font-size: 10pt; width: 100%;'>$htmlDiff</body></html>"
                    isEditable = false
                    background = JBColor.gray
                }

                val diffPanel = JPanel(BorderLayout()).apply {
                    alignmentX = LEFT_ALIGNMENT
                    add(diffPane, BorderLayout.CENTER)
                }
                threadPanel.add(diffPanel)

                threadComments.sortedBy { it.createdAt }.forEach { comment ->
                    val author = comment.user?.login ?: "Unknown"
                    val body = comment.body ?: ""
                    val time = comment.createdAt?.format(formatter) ?: ""
                    val commentHeader = "<html><b>$author</b> at $time</html>"

                    val headerPanel = JPanel(BorderLayout()).apply {
                        alignmentX = LEFT_ALIGNMENT
                        add(JBLabel(commentHeader), BorderLayout.WEST)
                    }

                    val commentPanel = JPanel(BorderLayout()).apply {
                        alignmentX = LEFT_ALIGNMENT
                        add(headerPanel, BorderLayout.NORTH)
                        add(JBTextArea(body).apply {
                            isEditable = false
                            lineWrap = true
                            wrapStyleWord = true
                        }, BorderLayout.CENTER)
                    }
                    threadPanel.add(commentPanel)
                }
                reviewPanel.add(threadPanel)
            }
    }

    private fun triggerCodeVisionUpdate() {
        ApplicationManager.getApplication().invokeLater {
            com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart()
            com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications()

            // Update inlays in open editors
            val fileEditorManager = FileEditorManager.getInstance(project)
            for (file in fileEditorManager.openFiles) {
                for (editor in fileEditorManager.getEditors(file)) {
                    if (editor is TextEditor) {
                        GiteaCommentInlayManager.updateInlaysAsync(editor.editor, file)
                    }
                }
            }
        }
    }

    fun showLoadingText() {
        contentPanel.removeAll()
        contentPanel.add(JBLabel("Loading...").apply {
            alignmentY = CENTER_ALIGNMENT
            alignmentX = CENTER_ALIGNMENT
            foreground = UIUtil.getInactiveTextColor()
        })
        revalidate()
        repaint()
    }
}
