import com.intellij.openapi.application.runWriteAction
import spp.plugin.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message

class AddMeterCommand : LiveCommand() {
    override val name = message("add_meter")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: ${getCommandHighlightColor()}\">" + message("on_line") +
            " *lineNumber*</span></html>"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            liveStatusManager.showMeterStatusBar(project.currentEditor!!, context.lineNumber)
        }
    }
}

if (liveInstrumentService != null) {
    registerCommand(AddMeterCommand())
}
