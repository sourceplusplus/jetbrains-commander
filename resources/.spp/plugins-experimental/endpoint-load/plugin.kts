import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.vertx.core.json.JsonObject
import spp.jetbrains.indicator.LiveIndicator
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.monitor.skywalking.model.DurationStep
import spp.jetbrains.monitor.skywalking.model.TopNCondition
import spp.jetbrains.monitor.skywalking.model.TopNCondition.Order
import spp.jetbrains.monitor.skywalking.model.TopNCondition.Scope
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.plugin.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class EndpointLoadIndicator(project: Project) : LiveIndicator(project) {
    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        val endpointName = guideMark.getUserData(EndpointDetector.ENDPOINT_NAME) ?: return
        if (EndpointDetector.ENDPOINT_NAME != event.params.firstOrNull()) return
        val highLoadEndpoints = getHighLoadEndpoints()

        if (highLoadEndpoints.contains(endpointName)) {
            ApplicationManager.getApplication().runReadAction {
                val gutterMark = ArtifactCreationService.createMethodGutterMark(
                    guideMark.sourceFileMarker,
                    (guideMark as MethodSourceMark).getPsiElement().nameIdentifier!!,
                    false
                )
                gutterMark.configuration.activateOnMouseHover = false //todo: show tooltip with extra info
                gutterMark.configuration.icon = findIcon("endpoint-load/icons/high-load-icon.svg")
                gutterMark.apply(true)
            }
        }
    }

    private suspend fun getHighLoadEndpoints(): List<String> {
        val endTime = ZonedDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(15)
        val duration = ZonedDuration(startTime, endTime, DurationStep.MINUTE)
        val service = skywalkingMonitorService.getCurrentService() ?: return emptyList()
        val highLoadEndpoints = skywalkingMonitorService.sortMetrics(
            TopNCondition(
                "endpoint_cpm",
                service.name,
                true,
                Scope.Endpoint,
                (skywalkingMonitorService.getEndpoints(service.name, 1000).size() * 0.20).toInt(), //relative top 20%
                Order.DES
            ), duration
        )

        return highLoadEndpoints.map { (it as JsonObject).getString("name") }
    }
}

registerIndicator(EndpointLoadIndicator(project))
