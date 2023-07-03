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
import io.vertx.kotlin.coroutines.await
import spp.jetbrains.SourceKey
import spp.jetbrains.marker.indicator.LiveIndicator
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
import spp.plugin.*
import spp.protocol.artifact.metrics.MetricStep
import spp.protocol.artifact.metrics.MetricType.Companion.Endpoint_SLA
import spp.protocol.platform.general.Order
import spp.protocol.platform.general.Scope
import spp.protocol.platform.general.SelectedRecord
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

class FailingEndpointIndicator(project: Project) : LiveIndicator(project) {

    companion object {
        private val INDICATOR_STARTED = IEventCode.getNewIEventCode()
        private val INDICATOR_STOPPED = IEventCode.getNewIEventCode()
        private val SLA = SourceKey<MutableMap<String, Float>>(this::class.simpleName + "_SLA")
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED, INDICATOR_STARTED, INDICATOR_STOPPED)
    private val failingEndpoints = hashMapOf<String, GuideMark>()
    private val failingIndicators = hashMapOf<GuideMark, GutterMark>()

    override suspend fun refreshIndicator() {
        val currentFailing = getTopFailingEndpoints()

        //trigger adds
        currentFailing.forEach {
            val endpointName = it.name
            val sla = it.value.toFloat() / 100.0f
            val startIndicator = !failingEndpoints.containsKey(endpointName)

            if (log.isTraceEnabled) log.trace("Endpoint $endpointName is failing. SLA: $sla")
            findByEndpointName(endpointName)?.let { guideMark ->
                failingEndpoints[endpointName] = guideMark
                guideMark.putUserDataIfAbsent(SLA, hashMapOf())
                guideMark.getUserData(SLA)!![endpointName] = sla

                if (startIndicator) {
                    guideMark.triggerEvent(INDICATOR_STARTED, listOf(endpointName))
                }
            }
        }

        //trigger removes
        val previousHighLoads = failingEndpoints.filter {
            !currentFailing.map { it.name }.contains(it.key)
        }
        previousHighLoads.forEach {
            log.debug("Endpoint ${it.key} is no longer failing")
            failingEndpoints.remove(it.key)?.triggerEvent(INDICATOR_STOPPED, listOf(it.key))
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
                    log.info("Adding failing endpoint indicator for: $endpointName")
                    val gutterMark = when (guideMark) {
                        is MethodSourceMark -> ArtifactCreationService.createMethodGutterMark(guideMark, false)
                        is ExpressionSourceMark -> ArtifactCreationService.createExpressionGutterMark(guideMark, false)
                        else -> throw IllegalStateException("Guide mark is not a method or expression")
                    }
                    gutterMark.configuration.activateOnMouseHover = false

                    val slaMap = guideMark.getUserData(SLA)!!
                    if (slaMap.size == 1) {
                        gutterMark.configuration.tooltipText = {
                            "Top 20% failing endpoint. SLA: ${slaMap[endpointName]}%"
                        }
                    } else {
                        gutterMark.configuration.tooltipText = {
                            "Top 20% failing endpoint.\n" + buildString {
                                slaMap.forEach { (endpoint, sla) ->
                                    appendLine(" - ${endpoint.substringBefore(":")}: $sla%")
                                }
                            }
                        }
                    }
                    gutterMark.configuration.icon = findIcon("icons/failing-endpoint.svg")
                    gutterMark.apply(true)
                    failingIndicators[guideMark] = gutterMark

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
                    failingEndpoints.remove(endpointName)
                    val gutterMark = failingIndicators.remove(guideMark) ?: return@runReadAction
                    log.info("Removing failing endpoint indicator for: $endpointName")
                    gutterMark.sourceFileMarker.removeSourceMark(gutterMark, autoRefresh = true)
                }
            }

            else -> refreshIndicator()
        }
    }

    private suspend fun getTopFailingEndpoints(): List<SelectedRecord> {
        if (log.isTraceEnabled) log.trace("Getting top failing endpoints")
        val endTime = ZonedDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES) //exclusive
        val startTime = endTime.minusMinutes(2)
        val service = statusService.getCurrentService() ?: return emptyList()
        return managementService.sortMetrics(
            Endpoint_SLA.metricId,
            service.name,
            true,
            Scope.Endpoint,
            ceil(managementService.getEndpoints(service, 1000).await().size * 0.20).toInt(), //top 20%
            Order.ASC,
            MetricStep.MINUTE,
            startTime.toInstant(),
            endTime.toInstant()
        ).await().filter { it.value.toDouble() < 10000.0 }
    }
}

registerIndicator(FailingEndpointIndicator(project))
