import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import spp.jetbrains.indicator.LiveIndicator
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.ExpressionSourceMark
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.gutter.GutterMark
import spp.plugin.findIcon
import spp.plugin.registerIndicator

class UnusedEndpointIndicator(project: Project) : LiveIndicator(project) {

    companion object {
        private val log = logger<UnusedEndpointIndicator>()
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)
    private val unusedIndicators = mutableMapOf<GuideMark, GutterMark>()

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (event.eventCode == MARK_USER_DATA_UPDATED && EndpointDetector.ENDPOINT_FOUND != event.params.firstOrNull()) {
            return //ignore other user data updates
        }

        if (event.params[1] as Boolean) {
            val endpointName = guideMark.getUserData(EndpointDetector.ENDPOINT_NAME)
            ApplicationManager.getApplication().runReadAction {
                val gutterMark = unusedIndicators.remove(guideMark) ?: return@runReadAction
                log.info("Removing unused endpoint indicator for: $endpointName")
                gutterMark.sourceFileMarker.removeSourceMark(gutterMark, autoRefresh = true)
            }
        } else {
            val endpointName = guideMark.getUserData(EndpointDetector.ENDPOINT_NAME)
            ApplicationManager.getApplication().runReadAction {
                log.info("Adding unused endpoint indicator for: $endpointName")
                val gutterMark = when (guideMark) {
                    is MethodSourceMark -> ArtifactCreationService.createMethodGutterMark(guideMark, false)
                    is ExpressionSourceMark -> ArtifactCreationService.createExpressionGutterMark(guideMark, false)
                    else -> throw IllegalStateException("Guide mark is not a method or expression")
                }
                gutterMark.configuration.activateOnMouseHover = false //todo: show tooltip with extra info
                gutterMark.configuration.icon = findIcon("icons/unused-endpoint.svg")
                gutterMark.apply(true)
                unusedIndicators[guideMark] = gutterMark
                gutterMark.addEventListener {
                    if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                        unusedIndicators.remove(guideMark)
                    }
                }
            }
        }
    }
}

registerIndicator(UnusedEndpointIndicator(project))
