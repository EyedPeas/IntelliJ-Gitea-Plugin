package gitea_plugin.components

import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import gitea_plugin.GlobalGiteaCache
import io.gitea.model.ChangedFile

class ChangedFilesOverviewPanelTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        GlobalGiteaCache.clear()
    }

    fun testTreeStructure() {
        val changedFiles = listOf(
            ChangedFile().apply { filename = "src/main/kotlin/File1.kt"; status = "modified" },
            ChangedFile().apply { filename = "src/main/kotlin/File2.kt"; status = "added" },
            ChangedFile().apply { filename = "src/test/File3Test.kt"; status = "deleted" },
            ChangedFile().apply { filename = "README.md"; status = "renamed"; previousFilename = "OLD_README.md" }
        )
        GlobalGiteaCache.setChangedFiles(changedFiles)

        val panel = ChangedFilesOverviewPanel(project)
        
        val getChangesToDisplayMethod = ChangedFilesOverviewPanel::class.java.getDeclaredMethod("getChangesToDisplay")
        getChangesToDisplayMethod.isAccessible = true
        val changes = getChangesToDisplayMethod.invoke(panel) as List<com.intellij.openapi.vcs.changes.Change>
        
        println("[DEBUG_LOG] Generated Changes:")
        for (change in changes) {
            println("[DEBUG_LOG]   - ${change.afterRevision?.file?.path}")
            assertFalse("Path should not contain redundant ../", change.afterRevision?.file?.path?.contains("../") ?: false)
        }
        
        assertTrue("Should have 4 changes", changes.size == 4)
    }

    fun testCollapseLogic() {
        val changedFiles = listOf(
            ChangedFile().apply { filename = "a/b/c/file.txt"; status = "modified" }
        )
        GlobalGiteaCache.setChangedFiles(changedFiles)

        val panel = ChangedFilesOverviewPanel(project)
        val treeField = ChangedFilesOverviewPanel::class.java.getDeclaredField("tree")
        treeField.isAccessible = true
        val tree = treeField.get(panel) as AsyncChangesTree
        tree.requestRefresh()
    }
}
