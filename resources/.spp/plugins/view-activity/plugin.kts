import spp.plugin.*
import spp.command.*
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message

/**
 * Opens the 'Endpoint-Activity' dashboard via portal popup.
 */
class ViewActivityCommand : LiveCommand() {
    override val name = message("view_activity")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_view") + " ➛ " + message("activity") + " ➛ " + message("scope") +
            ": </span><span style=\"color: ${getCommandHighlightColor()}\">" + message("method") +
            "</span></html>"
    override var selectedIcon = findIcon("icons/view-activity_selected.svg")
    override var unselectedIcon = findIcon("icons/view-activity_unselected.svg")

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.guideMark?.getUserData(EndpointDetector.ENDPOINT_ID) ?: return
        val serviceId = endpointId.substringBefore("_")
        val pageType = "Activity"
        val newPage = "/dashboard/GENERAL/Endpoint/$serviceId/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.guideMark!!.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.guideMark!!.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }
}

registerCommand(ViewActivityCommand())