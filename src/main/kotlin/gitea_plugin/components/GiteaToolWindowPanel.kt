package gitea_plugin.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import gitea_plugin.GiteaService
import gitea_plugin.GiteaSettings
import gitea_plugin.GiteaSettingsConfigurable
import gitea_plugin.GlobalGiteaCache
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class GiteaToolWindowPanel(private val project: Project) : JBPanel<GiteaToolWindowPanel>(GridLayout(3, 1)) {
    private var giteaService: GiteaService = GiteaService(project)
    private val prListPanel = PRListPanel { selectedPR ->
        GlobalGiteaCache.setLoadedPullRequest(selectedPR, project)
    }
    private val prCommentsPanel = PRCommentsPanel(project).apply {
        border = BorderFactory.createTitledBorder("Comments")
    }
    private val prDetailsPanel = PRDetailsPanel(project).apply {
        border = BorderFactory.createTitledBorder("Details")
    }

    init {
        fetchData()
    }

    private fun fetchData() {
        val token = GiteaSettings.Companion.getInstance().state.giteaToken
        if (token.isBlank()) {
            ApplicationManager.getApplication().invokeLater({
                removeAll()
                layout = BorderLayout()
                val messagePanel = JPanel(BorderLayout(0, 10))
                messagePanel.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

                val label = JBLabel(
                    "<html><center>Gitea API token is not set.<br>Please set it in <b>Settings &gt; Gitea</b> to access your repositories.</center></html>",
                    JLabel.CENTER
                )
                messagePanel.add(label, BorderLayout.CENTER)

                val buttons = JPanel()
                val retryButton = JButton("Retry")
                retryButton.addActionListener {
                    layout = GridLayout(3, 1)
                    fetchData()
                }
                buttons.add(retryButton)

                val settingsButton = JButton("Open Settings")
                settingsButton.addActionListener {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, GiteaSettingsConfigurable::class.java)
                }
                buttons.add(settingsButton)

                messagePanel.add(buttons, BorderLayout.SOUTH)

                add(messagePanel, BorderLayout.NORTH)
                revalidate()
                repaint()
            }, ModalityState.any())
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            var retryCount = 0
            val maxRetries = 10
            val delayMs = 500L

            while (!giteaService.isReady() && retryCount < maxRetries) {
                ApplicationManager.getApplication().invokeLater({
                    removeAll()
                    val prPanelLoading = JBLabel("Searching git repository...", AnimatedIcon.Default(), JLabel.CENTER)
                    add(prPanelLoading, BorderLayout.CENTER)
                    revalidate()
                    repaint()
                }, ModalityState.any())

                Thread.sleep(delayMs)
                retryCount++
            }

            if (!giteaService.isReady()) {
                ApplicationManager.getApplication().invokeLater({
                    removeAll()
                    add(prListPanel)
                    prListPanel.showMessage("No git repository with supported Gitea remote found after 5s.")
                }, ModalityState.any())
                return@executeOnPooledThread
            }

            // at the moment, this does nothing. But later, it can be used to get the current user and their open pull requests
            giteaService.fetchUserData(
                onSuccess = { email ->
                    ApplicationManager.getApplication().invokeLater({
                    }, ModalityState.any())
                },
                onError = {
                    ApplicationManager.getApplication().invokeLater({
                    }, ModalityState.any())
                }
            )

            giteaService.fetchPullRequests(
                onSuccess = { prs ->
                    ApplicationManager.getApplication().invokeLater({
                        removeAll()
                        add(JPanel().apply {
                            layout = BorderLayout()
                            val unloadButton = JButton("Unload Pull Request")
                            unloadButton.addActionListener {
                                GlobalGiteaCache.setReviewModeEnabled(false)
                                GlobalGiteaCache.setLoadedPullRequest(null, project)
                            }
                            add(unloadButton, BorderLayout.NORTH)
                            add(prListPanel, BorderLayout.CENTER)
                        })
                        add(prDetailsPanel)
                        add(prCommentsPanel)
                        prListPanel.updateList(prs)
                    }, ModalityState.any())
                },
                onError = {
                    ApplicationManager.getApplication().invokeLater({
                        prListPanel.clear()
                    }, ModalityState.any())
                }
            )
        }
    }
}