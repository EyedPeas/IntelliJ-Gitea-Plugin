package gitea_plugin.vision

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.gitea.model.PullReviewComment
import io.gitea.model.User

class GiteaCommentComponentTest : BasePlatformTestCase() {
    fun testUpdateWidthHeightChange() {
        myFixture.configureByText("test.txt", "")
        val editor = myFixture.editor
        val comment = PullReviewComment().apply {
            user = User().apply { login = "testuser" }
            resolver = null // Not resolved, so it's not collapsed by default
            body = "Line1\nLine2\nLine3\nLine4\nLine5"
        }
        
        val component = GiteaCommentComponent(editor, listOf(comment))
        // Set a default font so font metrics work in headless mode
        val font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
        component.font = font
        val fm = component.getFontMetrics(font)
        val lineHeight = fm.height
        
        // Very wide, should be 5 lines
        component.updateWidth(1000)
        val h1 = component.preferredSize.height
        
        // Very narrow, each line should wrap multiple times
        component.updateWidth(10)
        val h2 = component.preferredSize.height
        
        System.out.println("[DEBUG_LOG] Height at 1000: $h1")
        System.out.println("[DEBUG_LOG] Height at 10: $h2")
        System.out.println("[DEBUG_LOG] Line height: $lineHeight")
        
        assertTrue("Height at 10 ($h2) should be greater than height at 1000 ($h1)", h2 > h1)
    }

    fun testCommentWithoutResolver() {
        myFixture.configureByText("test2.txt", "")
        val editor = myFixture.editor
        val comment = PullReviewComment().apply {
            user = User().apply { login = "testuser" }
            resolver = null
            body = "test body"
        }
        val component = GiteaCommentComponent(editor, listOf(comment))
        // Basically if it doesn't crash, it's good as we don't have easy way to inspect the rendered text in a unit test easily without more complex reflection or mocks
        assertNotNull(component)
    }
}
