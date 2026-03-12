package gitea_plugin.vision

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Rectangle

class GiteaCommentInlayRenderer(
    private var component: GiteaCommentComponent? = null
) : EditorCustomElementRenderer {

    private fun getAvailableWidth(inlay: Inlay<*>): Int {
        val editor = inlay.editor
        val visibleArea = editor.scrollingModel.visibleArea
        val rightMargin = editor.settings.getRightMargin(editor.project)
        
        // Use visible width minus some buffer, but don't exceed the right margin if it's set
        val width = visibleArea.width - 100
        return if (rightMargin > 0) {
            val marginWidth = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(null)).charWidth('m') * rightMargin
            minOf(width, marginWidth)
        } else {
            width
        }.coerceAtLeast(200) // Minimum width
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return getAvailableWidth(inlay)
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val comp = component ?: inlay.getUserData(GITEA_INLAY_COMPONENT_KEY)
        if (comp != null) {
            comp.updateWidth(getAvailableWidth(inlay))
            return comp.preferredSize.height
        }
        return 10 // Fallback
    }

    override fun paint(inlay: Inlay<*>, g: java.awt.Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        // Swing components will be painted by the editor's content component
    }

    companion object {
        val GITEA_INLAY_COMPONENT_KEY = com.intellij.openapi.util.Key.create<GiteaCommentComponent>("GITEA_INLAY_COMPONENT")
    }
}
