package gitea_plugin.vision

import com.intellij.openapi.editor.Editor
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import io.gitea.model.PullReviewComment
import org.threeten.bp.format.DateTimeFormatter
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.text.View

class GiteaCommentComponent(
    private val editor: Editor,
    comments: List<PullReviewComment>
) : JBPanel<GiteaCommentComponent>(BorderLayout()) {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private val padding = 8

    init {
        isOpaque = false
        isVisible = false
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = editor.colorsScheme.defaultBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(editor.colorsScheme.defaultBackground.brighter()),
                BorderFactory.createEmptyBorder(padding / 2, padding, padding / 2, padding)
            )
        }

        comments.forEach { comment ->
            val author = comment.user?.login ?: "Unknown"
            val time = comment.createdAt?.format(formatter) ?: ""
            val body = comment.body ?: ""

            val headerPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                val authorLabel = JBLabel(author).apply {
                    font = font.deriveFont(Font.BOLD)
                    foreground = editor.colorsScheme.defaultForeground
                }
                val timeLabel = JBLabel(" at $time").apply {
                    font = font.deriveFont(Font.ITALIC)
                    foreground = JBColor.GRAY
                }
                add(authorLabel, BorderLayout.WEST)
                add(timeLabel, BorderLayout.CENTER)
            }

            val bodyArea = JBTextArea(body).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                font = editor.colorsScheme.getFont(null)
                background = editor.colorsScheme.defaultBackground
                foreground = editor.colorsScheme.defaultForeground
                border = BorderFactory.createEmptyBorder(2, 0, padding, 0)
            }

            mainPanel.add(headerPanel)
            mainPanel.add(bodyArea)
        }

        add(mainPanel, BorderLayout.CENTER)
        border = BorderFactory.createEmptyBorder(padding / 2, padding, padding / 2, padding)
    }

    fun updateWidth(maxWidth: Int) {
        preferredSize = null
        val width = maxWidth.coerceAtLeast(0)
        val mainPanel = components.firstOrNull() as? JPanel ?: return
        val innerWidth = (width - insets.left - insets.right).coerceAtLeast(0)
        val panelInsets = mainPanel.insets
        val textAreaWidth = (innerWidth - panelInsets.left - panelInsets.right).coerceAtLeast(0)

        mainPanel.components.forEach { child ->
            child.preferredSize = null
            if (child is JBTextArea) {
                val height = child.getUI().getRootView(child).let {
                    it.setSize(textAreaWidth.toFloat(), 10000f)
                    it.getPreferredSpan(View.Y_AXIS).toInt()
                }
                child.preferredSize = java.awt.Dimension(textAreaWidth, height)
            }
        }

        mainPanel.invalidate()
        val prefHeight = mainPanel.preferredSize.height
        preferredSize = java.awt.Dimension(width, prefHeight + insets.top + insets.bottom)
        doLayout()
    }
}
