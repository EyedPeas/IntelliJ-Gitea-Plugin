package gitea_plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import gitea_plugin.components.PRCommentsPanel
import io.gitea.model.PullRequest
import io.gitea.model.PullReview
import io.gitea.model.PullReviewComment
import io.gitea.model.User
import org.threeten.bp.OffsetDateTime
import javax.swing.JLabel
import javax.swing.JPanel

class PRCommentsPanelTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        GlobalGiteaCache.clear()
    }

    fun testResolvedCommentThreadHasHeaderWithTitle() {
        val project = project
        val pr = PullRequest().apply {
            number = 1L
        }
        // We can't easily mock GlobalGiteaCache as it's an object, but we can use its public methods to set state.
        // Actually, PRCommentsPanel calls GlobalGiteaCache.getLoadedPullRequest(), getReviews(), and getCommentsForReview(review.id)
        
        val resolverUser = User().apply { login = "resolver" }
        val authorUser = User().apply { login = "author" }
        
        val review = PullReview().apply {
            id = 123L
            user = authorUser
            submittedAt = OffsetDateTime.now()
            state = "COMMENT"
        }
        
        val comment = PullReviewComment().apply {
            id = 456L
            path = "test.kt"
            position = 10
            user = authorUser
            resolver = resolverUser
            body = "Resolved comment body"
            createdAt = OffsetDateTime.now()
        }
        
        // Setup cache state
        // Need a way to set loaded PR without triggering side effects if possible
        // GlobalGiteaCache.setLoadedPullRequest(pr, project) triggers async loadComments
        // Let's look at GlobalGiteaCache again to see if we can set things directly
        
        // Manual setup of cache fields via reflection to avoid side effects of setLoadedPullRequest
        val field = GlobalGiteaCache::class.java.getDeclaredField("loadedPullRequest")
        field.isAccessible = true
        field.set(GlobalGiteaCache, pr)
        
        GlobalGiteaCache.setReviews(listOf(review))
        GlobalGiteaCache.setCommentsForReview(review.id, listOf(comment))

        val panel = PRCommentsPanel(project)
        panel.refreshComments()
        
        val contentPanel = panel.contentPanel
        
        // Hierarchy should be: contentPanel -> reviewPanel (TitledBorder) -> threadPanel -> headerPanel -> titleLabel (JBLabel)
        
        val reviewPanel = contentPanel.components.filterIsInstance<JPanel>().firstOrNull()
        assertNotNull("Review panel should be present", reviewPanel)
        
        val threadPanel = reviewPanel!!.components.filterIsInstance<JPanel>().firstOrNull()
        assertNotNull("Thread panel should be present", threadPanel)
        
        val headerPanel = threadPanel!!.components.filterIsInstance<JPanel>().firstOrNull()
        assertNotNull("Header panel should be present", headerPanel)
        
        // In headerPanel, we expect a titleLabel and an arrowLabel (since it's resolved)
        val labels = headerPanel!!.components.filterIsInstance<JLabel>()
        val titleLabel = labels.find { it.text != null && it.text.contains("Thread: test.kt#10") }
        
        if (titleLabel == null) {
            val labelTexts = labels.map { "'${it.text}'" }
            fail("Title label with thread ID should be present. Found labels: $labelTexts")
        }
        assertTrue("Title label should indicate it is resolved", titleLabel!!.text.contains("[Resolved]"))
        assertTrue("Title label should show resolver", titleLabel.text.contains("Resolved by resolver"))
    }
}
