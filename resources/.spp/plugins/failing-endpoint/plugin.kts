import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.vertx.core.json.JsonObject
import spp.jetbrains.indicator.LiveIndicator
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.ExpressionSourceMark
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.IEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.api.key.SourceKey
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.gutter.GutterMark
import spp.jetbrains.monitor.skywalking.model.DurationStep
import spp.jetbrains.monitor.skywalking.model.TopNCondition
import spp.jetbrains.monitor.skywalking.model.TopNCondition.Order
import spp.jetbrains.monitor.skywalking.model.TopNCondition.Scope
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.plugin.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

class FailingEndpointIndicator(project: Project) : LiveIndicator(project) {

    companion object {
        private val INDICATOR_STARTED = IEventCode.getNewIEventCode()
        private val INDICATOR_STOPPED = IEventCode.getNewIEventCode()
        private val SLA = SourceKey<Float>(this::class.simpleName + "_SLA")
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED, INDICATOR_STARTED, INDICATOR_STOPPED)
    private val failingEndpoints = hashMapOf<String, GuideMark>()
    private val failingIndicators = hashMapOf<GuideMark, GutterMark>()

    override suspend fun refreshIndicator() {
        val currentFailing = getTopFailingEndpoints()

        //trigger adds
        currentFailing.forEach {
            val endpointName = it.getString("name")
            val sla = it.getString("value").toFloat() / 100.0f
            val startIndicator = !failingEndpoints.containsKey(endpointName)

            log.debug("Endpoint $endpointName is failing. SLA: $sla")
            findByEndpointName(endpointName)?.let { guideMark ->
                failingEndpoints[endpointName] = guideMark
                guideMark.putUserData(SLA, sla)

                if (startIndicator) {
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
                    val gutterMark = when (guideMark) {
                        is MethodSourceMark -> ArtifactCreationService.createMethodGutterMark(guideMark, false)
                        is ExpressionSourceMark -> ArtifactCreationService.createExpressionGutterMark(guideMark, false)
                        else -> throw IllegalStateException("Guide mark is not a method or expression")
                    }
                    gutterMark.configuration.activateOnMouseHover = false
                    gutterMark.configuration.tooltipText = {
                        "Top 20% failing endpoint. SLA: ${guideMark.getUserData(SLA)}%"
                    }
                    gutterMark.configuration.icon = findIcon("icons/failing-endpoint.svg")
                    gutterMark.apply(true)
                    failingIndicators[guideMark] = gutterMark

                    guideMark.addEventListener {
                        if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                            guideMark.triggerEvent(INDICATOR_STOPPED, listOf())
                        }
                    }
                }
            }

            INDICATOR_STOPPED -> {
                val endpointName = guideMark.getUserData(EndpointDetector.ENDPOINT_NAME)
                ApplicationManager.getApplication().runReadAction {
                    failingEndpoints.remove(endpointName)
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
                service.name,
                true,
                Scope.Endpoint,
                ceil(skywalkingMonitorService.getEndpoints(service.id, 1000).size() * 0.20).toInt(), //top 20%
                Order.ASC
            ), duration
        )

        return failingEndpoints
            .map { (it as JsonObject) }
            .filter { it.getString("value").toDouble() < 10000.0 }
    }
}

registerIndicator(FailingEndpointIndicator(project))
