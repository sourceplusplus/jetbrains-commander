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

class HighLoadEndpointIndicator(project: Project) : LiveIndicator(project) {

    companion object {
        private val INDICATOR_STARTED = IEventCode.getNewIEventCode()
        private val INDICATOR_STOPPED = IEventCode.getNewIEventCode()
        private val CPM = SourceKey<Int>(this::class.simpleName + "_CPM")
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED, INDICATOR_STARTED, INDICATOR_STOPPED)
    private val highLoadEndpoints = hashMapOf<String, GuideMark>()
    private val highLoadIndicators = hashMapOf<GuideMark, GutterMark>()

    override suspend fun refreshIndicator() {
        val currentHighLoads = getHighLoadEndpoints()

        //trigger adds
        currentHighLoads.forEach {
            val endpointName = it.getString("name")
            val cpm = it.getString("value").toFloat().toInt()
            val startIndicator = !highLoadEndpoints.containsKey(endpointName)

            log.debug("Endpoint $endpointName is high load. Calls per minute: $cpm")
            findByEndpointName(endpointName)?.let { guideMark ->
                highLoadEndpoints[endpointName] = guideMark
                guideMark.putUserData(CPM, cpm)

                if (startIndicator) {
                    guideMark.triggerEvent(INDICATOR_STARTED, listOf())
                }
            }
        }

        //trigger removes
        highLoadEndpoints.toMap().forEach {
            if (!currentHighLoads.map { it.getString("name") }.contains(it.key)) {
                log.debug("Endpoint ${it.key} is no longer high load")
                highLoadEndpoints.remove(it.key)?.triggerEvent(INDICATOR_STOPPED, listOf())
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
                    log.info("Adding high load endpoint indicator for: $endpointName")
                    val gutterMark = when (guideMark) {
                        is MethodSourceMark -> ArtifactCreationService.createMethodGutterMark(guideMark, false)
                        is ExpressionSourceMark -> ArtifactCreationService.createExpressionGutterMark(guideMark, false)
                        else -> throw IllegalStateException("Guide mark is not a method or expression")
                    }
                    gutterMark.configuration.activateOnMouseHover = false
                    gutterMark.configuration.tooltipText = {
                        "Top 20% highest load. Calls per minute: ${guideMark.getUserData(CPM)}ms"
                    }
                    gutterMark.configuration.icon = findIcon("icons/high-load-endpoint.svg")
                    gutterMark.apply(true)
                    highLoadIndicators[guideMark] = gutterMark

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
                    highLoadEndpoints.remove(endpointName)
                    val gutterMark = highLoadIndicators.remove(guideMark) ?: return@runReadAction
                    log.info("Removing high load endpoint indicator for: $endpointName")
                    gutterMark.sourceFileMarker.removeSourceMark(gutterMark, autoRefresh = true)
                }
            }

            else -> refreshIndicator()
        }
    }

    private suspend fun getHighLoadEndpoints(): List<JsonObject> {
        val endTime = ZonedDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(2)
        val duration = ZonedDuration(startTime, endTime, DurationStep.MINUTE)
        val service = skywalkingMonitorService.getCurrentService() ?: return emptyList()
        val highLoadEndpoints = skywalkingMonitorService.sortMetrics(
            TopNCondition(
                "endpoint_cpm",
                service.name,
                true,
                Scope.Endpoint,
                ceil(skywalkingMonitorService.getEndpoints(service.id, 1000).size() * 0.20).toInt(), //top 20%
                Order.DES
            ), duration
        )

        return highLoadEndpoints.map { (it as JsonObject) }
    }
}

registerIndicator(HighLoadEndpointIndicator(project))
