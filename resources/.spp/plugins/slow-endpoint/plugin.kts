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
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.gutter.GutterMark
import spp.jetbrains.monitor.skywalking.SkywalkingClient.DurationStep
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.jetbrains.sourcemarker.SourceMarkerPlugin.vertx
import spp.plugin.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

class SlowEndpointIndicator : LiveIndicator() {

    companion object {
        private val log = LoggerFactory.getLogger("spp.indicator.SlowEndpointIndicator")
        private val INDICATOR_STARTED = IEventCode.getNewIEventCode()
        private val INDICATOR_STOPPED = IEventCode.getNewIEventCode()
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED, INDICATOR_STARTED, INDICATOR_STOPPED)
    private val slowEndpoints = mutableMapOf<String, GuideMark>()
    private val slowIndicators = mutableMapOf<GuideMark, GutterMark>()
    private var periodicTimerId = -1L

    override suspend fun onRegister() {
        log.info("SlowEndpointIndicator registered")
        vertx.setPeriodic(5000) { timerId ->
            periodicTimerId = timerId
            GlobalScope.launch(vertx.dispatcher()) {
                refreshIndicators()
            }
        }
    }

    //todo: won't need with multi-project SourceMarkerPlugin support (live-platform/#475)
    override suspend fun onUnregister() {
        log.info("SlowEndpointIndicator unregistered")
        vertx.cancelTimer(periodicTimerId)
        slowEndpoints.clear()
        slowIndicators.clear()
    }

    private suspend fun refreshIndicators() {
        val currentSlowest = getTopSlowEndpoints()

        //trigger adds
        currentSlowest.forEach {
            val endpointName = it.getString("name")
            val respTime = it.getString("value").toFloat()
            if (!slowEndpoints.containsKey(endpointName)) {
                log.debug("Endpoint $endpointName is slow. Resp time: $respTime")

                findByEndpointName(endpointName)?.let { guideMark ->
                    slowEndpoints[endpointName] = guideMark
                    guideMark.triggerEvent(INDICATOR_STARTED, listOf())
                }
            }
        }

        //trigger removes
        slowEndpoints.toMap().forEach {
            if (!currentSlowest.map { it.getString("name") }.contains(it.key)) {
                log.debug("Endpoint ${it.key} is no longer slow")
                slowEndpoints.remove(it.key)?.triggerEvent(INDICATOR_STOPPED, listOf())
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
                    log.info("Adding slow endpoint indicator for: $endpointName")
                    val gutterMark = ArtifactCreationService.createMethodGutterMark(
                        guideMark.sourceFileMarker,
                        (guideMark as MethodSourceMark).getPsiElement().nameIdentifier!!,
                        false
                    )
                    gutterMark.configuration.activateOnMouseHover = false //todo: show tooltip with extra info
                    gutterMark.configuration.icon = findIcon("icons/slow-endpoint.svg")
                    gutterMark.apply(true)
                    slowIndicators[guideMark] = gutterMark
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
                    val gutterMark = slowIndicators.remove(guideMark) ?: return@runReadAction
                    log.info("Removing slow endpoint indicator for: $endpointName")
                    gutterMark.sourceFileMarker.removeSourceMark(gutterMark, autoRefresh = true)
                }
            }
            else -> refreshIndicators()
        }
    }

    private suspend fun getTopSlowEndpoints(): List<JsonObject> {
        if (log.isTraceEnabled) log.trace("Getting top slow endpoints")
        val endTime = ZonedDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(2)
        val duration = ZonedDuration(startTime, endTime, DurationStep.MINUTE)
        val service = skywalkingMonitorService.getCurrentService() ?: return emptyList()
        val slowestEndpoints = skywalkingMonitorService.sortMetrics(
            TopNCondition(
                "endpoint_resp_time",
                Optional.presentIfNotNull(service.name),
                Optional.presentIfNotNull(true),
                Optional.presentIfNotNull(Scope.Endpoint),
                ceil(skywalkingMonitorService.getEndpoints(service.id, 1000).size() * 0.20).toInt(), //top 20%
                Order.DES
            ), duration
        )

        return slowestEndpoints.map { (it as JsonObject) }
    }
}

registerIndicator(SlowEndpointIndicator())
