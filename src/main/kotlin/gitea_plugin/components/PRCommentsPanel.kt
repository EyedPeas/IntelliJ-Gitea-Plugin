package gitea_plugin.components

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.icons.AllIcons
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import gitea_plugin.GitUtils
import gitea_plugin.GlobalGiteaCache
import gitea_plugin.vision.GiteaCommentInlayManager
import io.gitea.model.PullReviewComment
import org.threeten.bp.format.DateTimeFormatter
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.BoxLayout.Y_AXIS

class PRCommentsPanel(private val project: Project) : JBPanel<PRCommentsPanel>(BorderLayout()) {
    private val gitUtils = GitUtils(project)
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    internal val contentPanel = ScrollablePanel().apply {
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

        var isUpdatingListener = java.util.concurrent.atomic.AtomicBoolean(false)
        GlobalGiteaCache.addListener { pr ->
            if (isUpdatingListener.getAndSet(true)) return@addListener
            ApplicationManager.getApplication().invokeLater {
                isUpdatingListener.set(false)
                if (pr != null) {
                    showLoadingText()
                } else {
                    refreshComments()
                }
            }
        }

        var isUpdatingReviewListener = java.util.concurrent.atomic.AtomicBoolean(false)
        GlobalGiteaCache.addReviewListener {
            if (isUpdatingReviewListener.getAndSet(true)) return@addReviewListener
            ApplicationManager.getApplication().invokeLater {
                isUpdatingReviewListener.set(false)
                refreshComments()
                triggerCodeVisionUpdate()
            }
        }
    }

    internal class ScrollablePanel : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(
            visibleRect: Rectangle?,
            orientation: Int,
            direction: Int
        ): Int = 10

        override fun getScrollableBlockIncrement(
            visibleRect: Rectangle?,
            orientation: Int,
            direction: Int
        ): Int = 100

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
        } else {
            val updateCommentsButton = JButton("Update Comments")
            add(updateCommentsButton, BorderLayout.SOUTH)

            updateCommentsButton.addActionListener {
                GlobalGiteaCache.updateComments(project, pr)
            }
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
                    addComments(comments, reviewPanel, review.isStale ?: false)

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

    private fun addComments(
        commentList: List<PullReviewComment>,
        reviewPanel: JPanel,
        isStale: Boolean
    ) {
        commentList.filter { it.path != null && (it.position != null || it.originalPosition != null) }
            .groupBy { (it.path!!.replace("\\", "/").trim('/')) to (it.position ?: it.originalPosition) }
            .forEach { (threadKey, threadComments) ->
                val (path, position) = threadKey
                val safePosition = position ?: 0
                val resolver = threadComments.map { it.resolver }.distinct().firstOrNull()
                val isResolved = resolver != null

                val threadPanel = createVerticalPanel().apply {
                    border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                }

                val headerPanel = JPanel(BorderLayout(5, 0)).apply {
                    alignmentX = LEFT_ALIGNMENT
                    val threadId = "$path#$safePosition"
                    var threadTitle = "Thread: $threadId"

                    if (isResolved && isStale) {
                        threadTitle = "[Outdated] [Resolved] $threadTitle - Resolved by ${resolver.login}"
                    }
                    if (isResolved && !isStale) {
                        threadTitle = "[Resolved] $threadTitle - Resolved by ${resolver.login}"
                    }
                    if (!isResolved && isStale) {
                        threadTitle = "[Outdated] $threadTitle"
                    }

                    val titleLabel = LinkLabel<Unit>(threadTitle, null) { _, _ ->
                        if (path.isNotEmpty() && position != null) {
                            gitUtils.openFileAtPosition(path, position.toLong())
                        }

                    }
                    add(titleLabel, BorderLayout.CENTER)
                }

                val contentWrapper = createVerticalPanel().apply {
                    alignmentX = LEFT_ALIGNMENT
                }

                if (isResolved) {
                    val arrowLabel = JBLabel(AllIcons.General.ArrowRight)
                    headerPanel.add(arrowLabel, BorderLayout.WEST)
                    contentWrapper.isVisible = false

                    val toggleAction = object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent?) {
                            contentWrapper.isVisible = !contentWrapper.isVisible
                            arrowLabel.icon =
                                if (contentWrapper.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
                            revalidate()
                            repaint()
                        }
                    }

                    // Allow clicking on arrow label too (it's part of headerPanel but just to be sure)
                    arrowLabel.addMouseListener(toggleAction)
                    arrowLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                }

                threadPanel.add(headerPanel)
                threadPanel.add(contentWrapper)

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
                    border = JBUI.Borders.empty(5, 20, 5, 0)
                }
                contentWrapper.add(diffPanel)

                threadComments.sortedBy { it.createdAt }.forEach { comment ->
                    val author = comment.user?.login ?: "Unknown"
                    val body = comment.body ?: ""
                    val time = comment.createdAt?.format(formatter) ?: ""
                    val commentHeader = "<html><b>$author</b> at $time</html>"

                    val commentHeaderPanel = JPanel(BorderLayout()).apply {
                        alignmentX = LEFT_ALIGNMENT
                        add(JBLabel(commentHeader), BorderLayout.WEST)
                    }

                    val commentPanel = JPanel(BorderLayout()).apply {
                        alignmentX = LEFT_ALIGNMENT
                        add(commentHeaderPanel, BorderLayout.NORTH)
                        add(JBTextArea(body).apply {
                            isEditable = false
                            lineWrap = true
                            wrapStyleWord = true
                        }, BorderLayout.CENTER)
                        border = JBUI.Borders.empty(5, 20, 5, 0)
                    }
                    contentWrapper.add(commentPanel)
                }
                reviewPanel.add(threadPanel)
            }
    }

    private fun triggerCodeVisionUpdate() {
        ApplicationManager.getApplication().invokeLater {
            DaemonCodeAnalyzer.getInstance(project).restart()
            EditorNotifications.getInstance(project).updateAllNotifications()

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
