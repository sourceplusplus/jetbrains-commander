import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveCommandLocation
import spp.plugin.*

class AddLogCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_log")
    override val description = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("on_line") +
            " *lineNumber*</span></html>"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            liveStatusManager.showLogStatusBar(project.currentEditor!!, context.lineNumber, false)
        }
    }

    override fun isAvailable(location: LiveCommandLocation): Boolean {
        return liveInstrumentService != null
                && location.insideFunction
                && !location.insideInfiniteLoop
    }
}

registerCommand(AddLogCommand(project))
