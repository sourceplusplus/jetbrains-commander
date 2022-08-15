import com.apollographql.apollo3.api.Optional
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import io.vertx.core.json.JsonObject
import monitor.skywalking.protocol.type.Order
import monitor.skywalking.protocol.type.Scope
import monitor.skywalking.protocol.type.TopNCondition
import spp.indicator.LiveIndicator
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.IEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.gutter.GutterMark
import spp.jetbrains.monitor.skywalking.SkywalkingClient.DurationStep
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.plugin.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

class FailingEndpointIndicator : LiveIndicator() {

    companion object {
        private val log = logger<FailingEndpointIndicator>()
        private val INDICATOR_STARTED = IEventCode.getNewIEventCode()
        private val INDICATOR_STOPPED = IEventCode.getNewIEventCode()
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED, INDICATOR_STARTED, INDICATOR_STOPPED)
    private val failingEndpoints = mutableMapOf<String, GuideMark>()
    private val failingIndicators = mutableMapOf<GuideMark, GutterMark>()

    override suspend fun refreshIndicator() {
        val currentFailing = getTopFailingEndpoints()

        //trigger adds
        currentFailing.forEach {
            val endpointName = it.getString("name")
            val sla = it.getString("value").toFloat()
            if (!failingEndpoints.containsKey(endpointName)) {
                log.debug("Endpoint $endpointName is failing. SLA: $sla")

                findByEndpointName(endpointName)?.let { guideMark ->
                    failingEndpoints[endpointName] = guideMark
                    guideMark.triggerEvent(INDICATOR_STARTED, listOf())
                }
            }
        }

        //trigger removes
        failingEndpoints.toMap().forEach {
            if (!currentFailing.map { it.getString("name") }.contains(it.key)) {
                log.debug("Endpoint $it is no longer failing")
                failingEndpoints.remove(it.key)?.triggerEvent(INDICATOR_STOPPED, listOf())
            }
        }
    }

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (event.eventCode == MARK_USER_DATA_UPDATED && EndpointDetector.ENDPOINT_NAME != event.params.firstOrNull()) {
            return //ignore other user data updates
        }

        when (event.eventCode) {
            INDICATOR_STARTED -> {
                val endpointName = guideMark.getUserData(EndpointDetector.ENDPOINT_NAME)
                ApplicationManager.getApplication().runReadAction {
                    log.info("Adding failing endpoint indicator for: $endpointName")
                    val gutterMark = ArtifactCreationService.createMethodGutterMark(
                        guideMark.sourceFileMarker,
                        (guideMark as MethodSourceMark).getPsiElement().nameIdentifier!!,
                        false
                    )
                    gutterMark.configuration.activateOnMouseHover = false //todo: show tooltip with extra info
                    gutterMark.configuration.icon = findIcon("icons/failing-endpoint.svg")
                    gutterMark.apply(true)
                    failingIndicators[guideMark] = gutterMark
                    gutterMark.addEventListener {
                        if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                            gutterMark.triggerEvent(INDICATOR_STOPPED, listOf())
                        }
                    }
                }
            }
            INDICATOR_STOPPED -> {
                val endpointName = guideMark.getUserData(EndpointDetector.ENDPOINT_NAME)
                ApplicationManager.getApplication().runReadAction {
                    val gutterMark = failingIndicators.remove(guideMark) ?: return@runReadAction
                    log.info("Removing failing endpoint indicator for: $endpointName")
                    gutterMark.sourceFileMarker.removeSourceMark(gutterMark, autoRefresh = true)
                }
            }
            else -> refreshIndicator()
        }
    }

    private suspend fun getTopFailingEndpoints(): List<JsonObject> {
        if (log.isTraceEnabled) log.trace("Getting top failing endpoints")
        val endTime = ZonedDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(2)
        val duration = ZonedDuration(startTime, endTime, DurationStep.MINUTE)
        val service = skywalkingMonitorService.getCurrentService() ?: return emptyList()
        val failingEndpoints = skywalkingMonitorService.sortMetrics(
            TopNCondition(
                "endpoint_sla",
                Optional.presentIfNotNull(service.name),
                Optional.presentIfNotNull(true),
                Optional.presentIfNotNull(Scope.Endpoint),
                ceil(skywalkingMonitorService.getEndpoints(service.id, 1000).size() * 0.20).toInt(), //top 20%
                Order.ASC
            ), duration
        )

        return failingEndpoints
            .map { (it as JsonObject) }
            .filter { it.getString("value").toDouble() < 10000.0 }
    }
}

registerIndicator(FailingEndpointIndicator())
