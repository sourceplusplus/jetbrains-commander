import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.marker.impl.ArtifactScopeService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG
import spp.plugin.*
import spp.protocol.platform.auth.RolePermission

/**
 * Opens the 'Endpoint-Logs' dashboard via portal popup.
 */
class ViewLogsCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_logs")
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_view") + " ➛ " + message("logs") + " ➛ " + message("scope") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("method") +
            "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.guideMark?.getUserData(EndpointDetector.ENDPOINT_ID) ?: return
        val serviceId = endpointId.substringBefore("_")
        val pageType = "Logs"
        val newPage = "/dashboard/GENERAL/Endpoint/$serviceId/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.guideMark!!.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.guideMark!!.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.VIEW_LOGS)) {
            return false
        }

        return ArtifactScopeService.isInsideFunction(context.element)
    }
}

registerCommand(ViewLogsCommand(project))
