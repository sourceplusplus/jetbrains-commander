import com.intellij.openapi.project.Project
import spp.command.*
import spp.jetbrains.marker.source.mark.api.SourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.plugin.*
import spp.protocol.artifact.ArtifactNameUtils

/**
 * Opens the 'Endpoint-Traces' dashboard via portal popup.
 */
class ViewTracesCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_traces")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_view") + " ➛ " + message("traces") + " ➛ " + message("scope") +
            ": </span><span style=\"color: ${getCommandHighlightColor()}\">" + message("method") +
            "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.guideMark?.getUserData(EndpointDetector.ENDPOINT_ID) ?: return
        val serviceId = endpointId.substringBefore("_")
        val pageType = "Traces"
        val newPage = "/dashboard/GENERAL/Endpoint/$serviceId/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.guideMark!!.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.guideMark!!.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }

    override fun isAvailable(sourceMark: SourceMark): Boolean {
        return ArtifactNameUtils.hasFunctionSignature(sourceMark.artifactQualifiedName)
    }
}

registerCommand(ViewTracesCommand(project))
