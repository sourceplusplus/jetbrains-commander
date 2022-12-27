/*
 * Source++, the continuous feedback platform for developers.
 * Copyright (C) 2022 CodeBrig, Inc.
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
import com.intellij.openapi.util.Computable
import com.intellij.ui.JBColor
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import spp.jetbrains.PluginBundle
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI
import spp.jetbrains.indicator.LiveIndicator
import spp.jetbrains.marker.service.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.inlay.config.InlayMarkVirtualText
import spp.plugin.*
import spp.protocol.artifact.metrics.MetricType
import spp.protocol.artifact.metrics.MetricType.Companion.Endpoint_CPM
import spp.protocol.artifact.metrics.MetricType.Companion.Endpoint_RespTime_AVG
import spp.protocol.artifact.metrics.MetricType.Companion.Endpoint_SLA
import spp.protocol.instrument.LiveSourceLocation
import spp.protocol.service.SourceServices.Subscribe.toLiveViewSubscriberAddress
import spp.protocol.utils.fromPerSecondToPrettyFrequency
import spp.protocol.view.LiveView
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent
import java.awt.Color

/**
 * Displays inlay marks with convenient metrics for a quick overview of the artifact.
 */
class QuickStatsIndicator(project: Project) : LiveIndicator(project) {

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)
    private val inlayForegroundColor = JBColor(Color.decode("#787878"), Color.decode("#787878"))

    /**
     * Wait for [GuideMark] with stats that can be displayed.
     * Currently only endpoints detected by [EndpointDetector] are supported.
     */
    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (EndpointDetector.DETECTED_ENDPOINTS != event.params.firstOrNull()) return
        displayQuickStatsInlay(guideMark)
    }

    private suspend fun displayQuickStatsInlay(guideMark: GuideMark) {
        val service = skywalkingMonitorService.getCurrentService()
        if (service == null) {
            log.warn("No service selected, skipping quick stats inlay")
            return
        }

        log.info("Displaying quick stats inlay on artifact: ${guideMark.artifactQualifiedName.identifier}")
        val swVersion = skywalkingMonitorService.getVersion()
        val listenMetrics = listOf(
            Endpoint_CPM.asRealtime().getMetricId(swVersion),
            Endpoint_RespTime_AVG.asRealtime().getMetricId(swVersion),
            Endpoint_SLA.asRealtime().getMetricId(swVersion)
        )

        val inlay = ApplicationManager.getApplication().runReadAction(Computable {
            when {
                guideMark.isMethodMark -> ArtifactCreationService.createMethodInlayMark(
                    guideMark.sourceFileMarker,
                    (guideMark as MethodSourceMark).getNameIdentifier(),
                    false
                )

                guideMark.isExpressionMark -> ArtifactCreationService.createExpressionInlayMark(
                    guideMark.sourceFileMarker,
                    guideMark.lineNumber,
                    false
                )

                else -> throw IllegalStateException("Guide mark is not a method or expression")
            }
        })

        inlay.configuration.virtualText = InlayMarkVirtualText(inlay, "")
        inlay.configuration.virtualText!!.textAttributes.foregroundColor = inlayForegroundColor
        inlay.configuration.virtualText!!.relativeFontSize = true
        if (PluginBundle.LOCALE.language == "zh") {
            inlay.configuration.virtualText!!.font = PluginUI.MICROSOFT_YAHEI_PLAIN_14
            inlay.configuration.virtualText!!.xOffset = 20
            inlay.configuration.virtualText!!.fontSize = -3.5f
        } else {
            inlay.configuration.virtualText!!.xOffset = 5
            inlay.configuration.virtualText!!.fontSize = -1.5f
        }
        inlay.configuration.activateOnMouseClick = false
        inlay.apply(true)

        //todo: support multiple endpoints (need way to cycle between them)
        viewService.addLiveView(
            LiveView(
                null,
                mutableSetOf(guideMark.getUserData(EndpointDetector.DETECTED_ENDPOINTS)!!.firstNotNullOf { it.name }),
                guideMark.artifactQualifiedName,
                LiveSourceLocation(guideMark.artifactQualifiedName.identifier, 0, service.id),
                LiveViewConfig("ACTIVITY", listenMetrics, -1)
            )
        ).onComplete {
            if (it.succeeded()) {
                val subscriptionId = it.result().subscriptionId!!
                vertx.eventBus().consumer<JsonObject>(toLiveViewSubscriberAddress(subscriptionId)) {
                    val viewEvent = LiveViewEvent(it.body())
                    inlay.configuration.virtualText!!.updateVirtualText(formatLiveEvent(viewEvent))
                }
                inlay.addEventListener {
                    if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                        viewService.removeLiveView(subscriptionId)
                    }
                }
            } else {
                show(it.cause().message!!)
            }
        }
    }

    private fun formatLiveEvent(event: LiveViewEvent): String {
        val metrics = JsonArray(event.metricsData)
        val sb = StringBuilder()
        for (i in 0 until metrics.size()) {
            val metric = metrics.getJsonObject(i)
            var value: String? = null
            if (metric.getNumber("percentage") != null) {
                value = (metric.getNumber("percentage").toDouble() / 100.0).toString() + "%"
            }
            if (value == null) value = metric.getNumber("value").toString()

            val metricType = MetricType(metric.getJsonObject("meta").getString("metricsName"))
            if (metricType.equalsIgnoringRealtime(Endpoint_CPM)) {
                value = (metric.getNumber("value").toDouble() / 60.0).fromPerSecondToPrettyFrequency { message(it) }
            }
            if (metricType.equalsIgnoringRealtime(Endpoint_RespTime_AVG)) {
                value += message("ms")
            }
            sb.append("${message(metricType.simpleName)}: $value")
            if (i < metrics.size() - 1) {
                sb.append(" | ")
            }
        }
        return sb.toString()
    }
}

registerIndicator(QuickStatsIndicator(project))
