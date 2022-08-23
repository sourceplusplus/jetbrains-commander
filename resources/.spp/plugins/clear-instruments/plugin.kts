import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveCommandLocation
import spp.plugin.*

class ClearInstrumentsCommand(project: Project) : LiveCommand(project) {
    override val name = "Clear Instruments"
    override val description = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_instrument") + " ➛ " + message("clear_all") + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        liveInstrumentService!!.clearAllLiveInstruments(null).onComplete {
            if (it.succeeded()) {
                show("Successfully cleared active live instrument(s)")
            } else {
                show(it.cause().message, notificationType = ERROR)
            }
        }
    }

    override fun isAvailable(location: LiveCommandLocation): Boolean {
        return liveInstrumentService != null
    }
}

registerCommand(ClearInstrumentsCommand(project))
