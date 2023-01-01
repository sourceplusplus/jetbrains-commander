/*
 * Source++, the continuous feedback platform for developers.
 * Copyright (C) 2022-2023 CodeBrig, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.vertx.core.json.JsonObject
import spp.jetbrains.SourceKey
import spp.jetbrains.indicator.LiveIndicator
import spp.jetbrains.marker.service.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.ExpressionSourceMark
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.IEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.gutter.GutterMark
import spp.jetbrains.monitor.skywalking.model.DurationStep
import spp.jetbrains.monitor.skywalking.model.TopNCondition
import spp.jetbrains.monitor.skywalking.model.TopNCondition.Order
import spp.jetbrains.monitor.skywalking.model.TopNCondition.Scope
import spp.jetbrains.monitor.skywalking.model.ZonedDuration
import spp.plugin.*
import spp.protocol.artifact.metrics.MetricType.Companion.Endpoint_RespTime_AVG
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

class SlowEndpointIndicator(project: Project) : LiveIndicator(project) {

    companion object {
        private val INDICATOR_STARTED = IEventCode.getNewIEventCode()
        private val INDICATOR_STOPPED = IEventCode.getNewIEventCode()
        private val RESP_TIME = SourceKey<MutableMap<String, Float>>(this::class.simpleName + "_RESP_TIME")
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED, INDICATOR_STARTED, INDICATOR_STOPPED)
    private val slowEndpoints = hashMapOf<String, GuideMark>()
    private val slowIndicators = hashMapOf<GuideMark, GutterMark>()
    private lateinit var skywalkingVersion: String

    override suspend fun refreshIndicator() {
        skywalkingVersion = skywalkingMonitorService.getVersion()
        val currentSlowest = getTopSlowEndpoints()

        //trigger adds
        currentSlowest.forEach {
            val endpointName = it.getString("name")
            val respTime = it.getString("value").toFloat()
            val startIndicator = !slowEndpoints.containsKey(endpointName)

            log.debug("Endpoint $endpointName is slow. Resp time: $respTime")
            findByEndpointName(endpointName)?.let { guideMark ->
                slowEndpoints[endpointName] = guideMark
                guideMark.putUserDataIfAbsent(RESP_TIME, hashMapOf<String, Float>())
                guideMark.getUserData(RESP_TIME)!![endpointName] = respTime

                if (startIndicator) {
                    guideMark.triggerEvent(INDICATOR_STARTED, listOf(endpointName))
                }
            }
        }

        //trigger removes
        val previousSlowest = slowEndpoints.filter {
            !currentSlowest.map { it.getString("name") }.contains(it.key)
        }
        previousSlowest.forEach {
            log.debug("Endpoint ${it.key} is no longer slow")
            slowEndpoints.remove(it.key)?.triggerEvent(INDICATOR_STOPPED, listOf(it.key))
        }
    }

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (event.eventCode == MARK_USER_DATA_UPDATED && EndpointDetector.DETECTED_ENDPOINTS != event.params.firstOrNull()) {
            return //ignore other user data updates
        }

        when (event.eventCode) {
            INDICATOR_STARTED -> {
                val endpointName = event.params.first() as String
                ApplicationManager.getApplication().runReadAction {
                    log.info("Adding slow endpoint indicator for: $endpointName")
                    val gutterMark = when (guideMark) {
                        is MethodSourceMark -> ArtifactCreationService.createMethodGutterMark(guideMark, false)
                        is ExpressionSourceMark -> ArtifactCreationService.createExpressionGutterMark(guideMark, false)
                        else -> throw IllegalStateException("Guide mark is not a method or expression")
                    }
                    gutterMark.configuration.activateOnMouseHover = false

                    val respTimeMap = guideMark.getUserData(RESP_TIME)!!
                    if (respTimeMap.size == 1) {
                        gutterMark.configuration.tooltipText = {
                            "Top 20% slowest endpoint. Response time: ${respTimeMap[endpointName]}ms"
                        }
                    } else {
                        gutterMark.configuration.tooltipText = {
                            "Top 20% slowest endpoint. Response time:\n" + buildString {
                                respTimeMap.forEach { (endpoint, respTime) ->
                                    appendLine(" - ${endpoint.substringBefore(":")}: ${respTime}ms")
                                }
                            }
                        }
                    }
                    gutterMark.configuration.icon = findIcon("icons/slow-endpoint.svg")
                    gutterMark.apply(true)
                    slowIndicators[guideMark] = gutterMark

                    guideMark.addEventListener {
                        if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                            guideMark.triggerEvent(INDICATOR_STOPPED, listOf(endpointName))
                        }
                    }
                }
            }

            INDICATOR_STOPPED -> {
                val endpointName = event.params.first() as String
                ApplicationManager.getApplication().runReadAction {
                    slowEndpoints.remove(endpointName)
                    val gutterMark = slowIndicators.remove(guideMark) ?: return@runReadAction
                    log.info("Removing slow endpoint indicator for: $endpointName")
                    gutterMark.sourceFileMarker.removeSourceMark(gutterMark, autoRefresh = true)
                }
            }

            else -> refreshIndicator()
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
                Endpoint_RespTime_AVG.getMetricId(skywalkingVersion),
                service.name,
                true,
                Scope.Endpoint,
                ceil(skywalkingMonitorService.getEndpoints(service.id, 1000).size() * 0.20).toInt(), //top 20%
                Order.DES
            ), duration
        )

        return slowestEndpoints.map { (it as JsonObject) }
    }
}

registerIndicator(SlowEndpointIndicator(project))
