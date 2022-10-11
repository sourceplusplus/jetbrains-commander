import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.marker.service.ArtifactScopeService
import spp.plugin.*
import spp.protocol.platform.auth.RolePermission

class AddSpanCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_span")
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("on_method") +
            " *methodName*</span></html>"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            statusManager.showSpanStatusBar(project.currentEditor!!, context.lineNumber)
        }
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.ADD_LIVE_SPAN)) {
            return false
        }

        return instrumentService != null
                && ArtifactScopeService.isInsideFunction(context.element)
                && !ArtifactScopeService.isInsideEndlessLoop(context.element)
    }
}

registerCommand(AddSpanCommand(project))
