package gitea_plugin

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "gitea_plugin.GiteaSettings", storages = [Storage("gitea_settings.xml")])
class GiteaSettings : PersistentStateComponent<GiteaSettings.State> {
    class State {
        var giteaToken: String = ""
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    companion object {
        fun getInstance(): GiteaSettings = service()
    }
}
