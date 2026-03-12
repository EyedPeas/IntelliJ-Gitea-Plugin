package gitea_plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class MyToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowPanel = GiteaToolWindowPanel(project)
        val prOverview = ContentFactory.getInstance().createContent(toolWindowPanel, "PR Overview", false)
        toolWindow.contentManager.addContent(prOverview)
        val prComments = PRCommentsPanel(project)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(prComments, "Comments", false))

    }
}