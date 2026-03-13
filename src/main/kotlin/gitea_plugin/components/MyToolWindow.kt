package gitea_plugin.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import gitea_plugin.components.FilesChangedOverviewPanel
import gitea_plugin.components.GiteaToolWindowPanel

class MyToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowPanel = GiteaToolWindowPanel(project)
        val prOverview = ContentFactory.getInstance().createContent(toolWindowPanel, "PR Overview", false)
        val prComments = PRCommentsPanel(project)
        val filesChangedOverview = FilesChangedOverviewPanel(project)
        toolWindow.contentManager.addContent(prOverview)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(prComments, "Comments", false))


    }
}