package gitea_plugin

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class GiteaSettingsConfigurable : Configurable {
    private var myTokenField: JBPasswordField? = null

    override fun getDisplayName(): String = "Gitea"

    override fun createComponent(): JComponent {
        myTokenField = JBPasswordField()
        myTokenField!!.text = GiteaSettings.getInstance().state.giteaToken
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Gitea Token:", myTokenField!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        return String(myTokenField?.password ?: charArrayOf()) != GiteaSettings.getInstance().state.giteaToken
    }

    override fun apply() {
        GiteaSettings.getInstance().state.giteaToken = String(myTokenField?.password ?: charArrayOf())
    }

    override fun reset() {
        myTokenField?.text = GiteaSettings.getInstance().state.giteaToken
    }

    override fun disposeUIResources() {
        myTokenField = null
    }
}
