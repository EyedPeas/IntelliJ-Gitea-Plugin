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
            resolver = User().apply { login = "resolverUser" }
            body = "This is a very long comment that should wrap if the width is small enough. ".repeat(20)
        }
        
        val component = GiteaCommentComponent(editor, listOf(comment))
        
        component.updateWidth(200)
        val h1 = component.preferredSize.height
        
        component.updateWidth(800)
        val h2 = component.preferredSize.height
        
        println("[DEBUG_LOG] Height at 200: $h1")
        println("[DEBUG_LOG] Height at 800: $h2")
        
        assertTrue("Height at 200 ($h1) should be greater than height at 800 ($h2)", h1 > h2)
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
