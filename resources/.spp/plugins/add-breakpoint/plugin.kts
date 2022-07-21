import com.intellij.openapi.application.runWriteAction
import spp.plugin.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.status.LiveStatusManager

class AddBreakpointCommand : LiveCommand() {
    override val name = message("add_breakpoint")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: ${getCommandHighlightColor()}\">" + message("on_line") +
            " *lineNumber*</span></html>"
    override var selectedIcon = findIcon("icons/live-breakpoint_selected.svg")
    override var unselectedIcon = findIcon("icons/live-breakpoint_unselected.svg")

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            LiveStatusManager.showBreakpointStatusBar(project.currentEditor!!, context.lineNumber)
        }
    }
}

if (liveInstrumentService != null) {
    registerCommand(AddBreakpointCommand())
}
