package gitea_plugin.vision

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Font
import java.awt.FontMetrics
import java.awt.image.BufferedImage

class GiteaCommentInlayRendererTest {

    private fun getFontMetrics(): FontMetrics {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        val font = Font("Monospaced", Font.PLAIN, 12)
        return g.getFontMetrics(font)
    }

    private fun wrapText(text: String, metrics: FontMetrics, maxWidth: Int): List<String> {
        val result = mutableListOf<String>()
        text.lines().forEach { line ->
            if (metrics.stringWidth(line) <= maxWidth) {
                result.add(line)
            } else {
                var currentLine = ""
                val words = line.split(" ")
                for (i in words.indices) {
                    val word = words[i]
                    
                    // Try to add the word to the current line
                    val potentialLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    
                    if (metrics.stringWidth(potentialLine) <= maxWidth) {
                        currentLine = potentialLine
                    } else {
                        // Word doesn't fit on the current line
                        if (currentLine.isNotEmpty()) {
                            // Finish the current line (with a space if not the last line of the paragraph)
                            result.add("$currentLine ") 
                            currentLine = ""
                        }
                        
                        // Now handle the word that didn't fit
                        if (metrics.stringWidth(word) <= maxWidth) {
                            currentLine = word
                        } else {
                            // Word is too long, must break it
                            var remaining = word
                            while (remaining.isNotEmpty()) {
                                var breakIndex = 1
                                while (breakIndex <= remaining.length && metrics.stringWidth(remaining.substring(0, breakIndex)) <= maxWidth) {
                                    breakIndex++
                                }
                                breakIndex--
                                if (breakIndex == 0) breakIndex = 1
                                
                                val part = remaining.substring(0, breakIndex)
                                remaining = remaining.substring(breakIndex)
                                
                                if (remaining.isNotEmpty()) {
                                    result.add(part)
                                } else {
                                    currentLine = part
                                }
                            }
                        }
                    }
                }
                if (currentLine.isNotEmpty()) {
                    result.add(currentLine)
                }
            }
        }
        return result
    }

    @Test
    fun testWrapLongUrl() {
        val metrics = getFontMetrics()
        val url = "https://swagger.io/docs/specification/v3_0/describing-parameters/"
        val maxWidth = metrics.stringWidth("https://swagger.io/docs/") // Break it roughly in half
        
        val wrapped = wrapText(url, metrics, maxWidth)
        
        println("[DEBUG_LOG] URL: $url")
        println("[DEBUG_LOG] Max Width: $maxWidth")
        println("[DEBUG_LOG] Wrapped lines:")
        wrapped.forEach { println("[DEBUG_LOG]   '$it'") }
        
        // Ensure the URL is actually wrapped and not lost
        assertEquals(url, wrapped.joinToString(""))
        assert(wrapped.size > 1) { "URL should be wrapped into multiple lines" }
    }

    @Test
    fun testWrapUrlWithLeadingSpace() {
        val metrics = getFontMetrics()
        val text = "Link: https://swagger.io/docs/specification/v3_0/describing-parameters/"
        val maxWidth = metrics.stringWidth("Link: htt")
        
        val wrapped = wrapText(text, metrics, maxWidth)
        
        println("[DEBUG_LOG] Text: '$text'")
        println("[DEBUG_LOG] Wrapped lines:")
        wrapped.forEach { println("[DEBUG_LOG]   '$it'") }
        
        assertEquals(text, wrapped.joinToString(""))
    }
}
