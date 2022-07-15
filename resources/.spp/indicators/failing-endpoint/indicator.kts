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
import spp.jetbrains.marker.source.mark.api.event.IEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.gutter.GutterMark
import spp.jetbrains.monitor.skywalking.SkywalkingClient.DurationStep
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.jetbrains.sourcemarker.SourceMarkerPlugin.vertx
import spp.plugin.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class FailingEndpointIndicator : LiveIndicator() {

    companion object {
        private val log = LoggerFactory.getLogger("spp.indicator.FailingEndpointIndicator")
        private val INDICATOR_STARTED = IEventCode.getNewIEventCode()
        private val INDICATOR_STOPPED = IEventCode.getNewIEventCode()
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED, INDICATOR_STARTED, INDICATOR_STOPPED)
    private val failingEndpoints = mutableMapOf<String, GuideMark>()
    private val failingIndicators = mutableMapOf<GuideMark, GutterMark>()

    override suspend fun onRegister() {
        vertx.setPeriodic(5000) {
            GlobalScope.launch(vertx.dispatcher()) {
                refreshIndicators()
            }
        }
    }

    private suspend fun refreshIndicators() {
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
            else -> refreshIndicators()
        }
    }

    private suspend fun getTopFailingEndpoints(): List<JsonObject> {
        val endTime = ZonedDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(2)
        val duration = ZonedDuration(startTime, endTime, DurationStep.MINUTE)
        val failingEndpoints = skywalkingMonitorService.sortMetrics(
            TopNCondition(
                "endpoint_sla",
                Optional.presentIfNotNull(skywalkingMonitorService.getCurrentService().name),
                Optional.presentIfNotNull(true),
                Optional.presentIfNotNull(Scope.Endpoint),
                3, //todo: relative 10%
                Order.ASC
            ), duration
        )

        return failingEndpoints
            .map { (it as JsonObject) }
            .filter { it.getString("value").toDouble() < 10000.0 }
    }
}

registerIndicator(FailingEndpointIndicator())
