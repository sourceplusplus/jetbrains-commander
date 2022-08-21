import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.project.Project
import spp.command.LiveCommand
import spp.command.LiveCommandContext
import spp.jetbrains.marker.source.mark.api.SourceMark
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.PluginUI.getCommandTypeColor
import spp.plugin.*

class ClearInstrumentsCommand(project: Project) : LiveCommand(project) {
    override val name = "Clear Instruments"
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
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

    override fun isAvailable(sourceMark: SourceMark): Boolean {
        return liveInstrumentService != null
    }
}

registerCommand(ClearInstrumentsCommand(project))
