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

    override fun isAvailable(selfInfo: SelfInfo, element: PsiElement): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.ADD_LIVE_SPAN)) {
            return false
        }

        return liveInstrumentService != null
                && ArtifactScopeService.isInsideFunction(element)
                && !ArtifactScopeService.isInsideEndlessLoop(element)
    }
}

registerCommand(AddSpanCommand(project))
