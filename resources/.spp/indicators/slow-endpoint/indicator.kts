import com.apollographql.apollo3.api.Optional
import com.intellij.openapi.application.ApplicationManager
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import monitor.skywalking.protocol.type.Order
import monitor.skywalking.protocol.type.Scope
import monitor.skywalking.protocol.type.TopNCondition
import org.slf4j.LoggerFactory
import spp.indicator.LiveIndicator
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.monitor.skywalking.SkywalkingClient.DurationStep
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.jetbrains.sourcemarker.SourceMarkerPlugin.vertx
import spp.plugin.findIcon
import spp.plugin.registerIndicator
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class SlowEndpointIndicator : LiveIndicator() {

    private val log = LoggerFactory.getLogger("spp.indicator.SlowEndpointIndicator")
    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)

    override suspend fun triggerSuspend(guideMark: GuideMark, event: SourceMarkEvent) {
        val endpointName = guideMark.getUserData(EndpointDetector.ENDPOINT_NAME) ?: return
        if (EndpointDetector.ENDPOINT_NAME != event.params.firstOrNull()) return
        val slowestEndpoints = getTopSlowestEndpoints()

        if (slowestEndpoints.contains(endpointName)) {
            ApplicationManager.getApplication().runReadAction {
                log.info("Slow endpoint detected: $endpointName")
                val gutterMark = ArtifactCreationService.createMethodGutterMark(
                    guideMark.sourceFileMarker,
                    (guideMark as MethodSourceMark).getPsiElement().nameIdentifier!!,
                    false
                )
                gutterMark.configuration.activateOnMouseHover = false //todo: show tooltip with extra info
                gutterMark.configuration.icon = findIcon("slow-endpoint/icons/slow-endpoint.svg")
                gutterMark.apply(true)

                //ensure still slow
                vertx.setPeriodic(5000) { periodicId ->
                    GlobalScope.launch(vertx.dispatcher()) {
                        if (!getTopSlowestEndpoints().contains(endpointName)) {
                            log.info("Slow endpoint removed: $endpointName")
                            gutterMark.sourceFileMarker.removeSourceMark(gutterMark)
                            vertx.cancelTimer(periodicId)
                        }
                    }
                }
            }
        }
    }

    private suspend fun getTopSlowestEndpoints(): List<String> {
        val endTime = ZonedDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(2)
        val duration = ZonedDuration(startTime, endTime, DurationStep.MINUTE)
        val slowestEndpoints = skywalkingMonitorService.sortMetrics(
            TopNCondition(
                "endpoint_resp_time",
                Optional.presentIfNotNull(skywalkingMonitorService.getCurrentService().name),
                Optional.presentIfNotNull(true),
                Optional.presentIfNotNull(Scope.Endpoint),
                3, //todo: relative 10%
                Order.DES
            ), duration
        )

        return slowestEndpoints.map { (it as JsonObject).getString("name") }
    }
}

registerIndicator(SlowEndpointIndicator())
