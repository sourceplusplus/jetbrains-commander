import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.marker.impl.ArtifactScopeService
import spp.plugin.*
import spp.protocol.platform.auth.RolePermission
import spp.protocol.platform.developer.SelfInfo

class AddBreakpointCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_breakpoint")
    override val description = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("on_line") +
            " *lineNumber*</span></html>"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            liveStatusManager.showBreakpointStatusBar(project.currentEditor!!, context.lineNumber)
        }
    }

    override fun isAvailable(selfInfo: SelfInfo, element: PsiElement): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.ADD_LIVE_BREAKPOINT)) {
            return false
        }

        return liveInstrumentService != null
                && ArtifactScopeService.isInsideFunction(element)
                && !ArtifactScopeService.isInsideEndlessLoop(element)
    }
}

registerCommand(AddBreakpointCommand(project))
