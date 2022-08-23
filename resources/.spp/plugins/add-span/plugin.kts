import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveCommandLocation
import spp.plugin.*

class AddSpanCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_span")
    override val description = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("on_method") +
            " *methodName*</span></html>"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            liveStatusManager.showSpanStatusBar(project.currentEditor!!, context.lineNumber)
        }
    }

    override fun isAvailable(location: LiveCommandLocation): Boolean {
        return liveInstrumentService != null
                && location.insideFunction
                && !location.insideInfiniteLoop
    }
}

registerCommand(AddSpanCommand(project))
