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
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import spp.jetbrains.PluginBundle
import spp.jetbrains.PluginUI
import spp.jetbrains.SourceKey
import spp.jetbrains.artifact.model.FunctionArtifact
import spp.jetbrains.artifact.service.toArtifact
import spp.jetbrains.marker.SourceMarkerUtils
import spp.jetbrains.marker.indicator.LiveIndicator
import spp.jetbrains.marker.service.ArtifactCreationService
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_ADDED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.inlay.InlayMark
import spp.jetbrains.marker.source.mark.inlay.config.InlayMarkVirtualText
import spp.plugin.*
import spp.protocol.artifact.metrics.MetricStep
import spp.protocol.instrument.LiveMeter
import spp.protocol.platform.general.Service
import java.awt.Color
import java.time.Instant

class TestStabilityIndicator(project: Project) : LiveIndicator(project) {

    companion object {
        private val inlayForegroundColor = JBColor(Color.decode("#787878"), Color.decode("#787878"))
        private val INLAY = SourceKey<InlayMark>(this::class.simpleName + "_INLAY")
    }

    override val listenForEvents = listOf(MARK_ADDED)

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        val service = statusService.getCurrentService()
        if (service == null) {
            log.warn("No service selected")
            return
        }//todo: ensure necessary live views/instruments are already set up

        //find functions annotated with org.junit.jupiter.api.Test
        val testAnnotation = SourceMarkerUtils.doOnReadThread {
            val annotations = (guideMark.getPsiElement().toArtifact() as? FunctionArtifact)?.getAnnotations()
            annotations?.find { it.text == "@Test" }  //todo: type check
        }
        if (testAnnotation != null) {
            val testName = "public void " + guideMark.artifactQualifiedName.identifier
            log.info("Found test function: $testName")
            val inlay = guideMark.getUserData(INLAY) ?: dumbService.runReadActionInSmartMode(Computable {
                ArtifactCreationService.createExpressionInlayMark(guideMark.sourceFileMarker, testAnnotation)
            })
            if (guideMark.getUserData(INLAY) == null) {
                guideMark.putUserData(INLAY, inlay)
                setupVirtualTextInlay(inlay, testName, service)
            }

            val formattedTestName = LiveMeter.formatMeterName(testName)
            val fullFormattedTestName = "spp_junit_test_successful_$formattedTestName"
            val historicalView = viewService.getHistoricalMetrics(
                listOf(service.id),
                listOf(fullFormattedTestName),
                MetricStep.DAY,
                Instant.now()
            ).await()
            println(historicalView.data)

            var successCount = 0
            var totalCount = 0
            var successRate: Int? = null
            historicalView.data.forEach {
                val metric = it as JsonObject
                if (metric.getString("metricId") == fullFormattedTestName) {
                    successRate = metric.getInteger("value")
                }
            }
            log.debug("Test $testName success rate: $successRate%")// ($successCount/$totalCount)")

            inlay.configuration.virtualText!!.updateVirtualText(
                getTestStatusRichText(successRate, successCount, totalCount)
            )
        }
    }

    private fun getTestStatusRichText(successRate: Int?, successCount: Int, totalCount: Int): RichText {
        if (successRate == null) {
            return RichText().apply {
                append(" [N/A]", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }

        return RichText().apply {
            append(" - ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(successRate.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(" ($successCount/$totalCount)", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
    }

    private fun setupVirtualTextInlay(inlay: InlayMark, testName: String, service: Service) {
        log.info("Setting up virtual text inlay for $inlay")
        inlay.configuration.virtualText = InlayMarkVirtualText(inlay, " [Fetching...]").apply {
            useInlinePresentation = true
            relatesToPrecedingText = true
            textAttributes.foregroundColor = inlayForegroundColor
            relativeFontSize = true
            if (PluginBundle.LOCALE.language == "zh") {
                font = PluginUI.MICROSOFT_YAHEI_PLAIN_14
                xOffset = 20
                fontSize = -3.5f
            } else {
                xOffset = 5
                fontSize = -1.5f
            }
        }
        inlay.apply(true)

//        //listen for live view events
//        viewService.addLiveView(
//            LiveView(
//                entityIds = mutableSetOf(service.id),
//                artifactLocation = LiveSourceLocation(
//                    source = testName,
//                    service = service
//                ),
//                viewConfig = LiveViewConfig("TEST_STABILITY", listOf("spp_junit_test_successful"))
//            )
//        ).onComplete {
//            if (it.succeeded()) {
//                val subscriptionId = it.result().subscriptionId!!
//                vertx.eventBus().consumer(toLiveViewSubscription(subscriptionId)) {
//                    val viewEvent = LiveViewEvent(it.body())
//                    val metricsData = JsonObject(viewEvent.metricsData)
//                    val value = metricsData.getJsonObject("value")
//                    val successCount = value.getInteger(testName)
//                    inlay.configuration.virtualText!!.updateVirtualText(RichText().apply {
//                        append(" Success count: $successCount ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
//                    })
//                }
//                inlay.addEventListener {
//                    if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
//                        viewService.removeLiveView(subscriptionId)
//                    }
//                }
//            } else {
//                show(it.cause().message!!)
//            }
//        }
//        viewService.addLiveView(
//            LiveView(
//                entityIds = mutableSetOf(service.id),
//                artifactLocation = LiveSourceLocation(
//                    source = testName,
//                    service = service
//                ),
//                viewConfig = LiveViewConfig("TEST_STABILITY", listOf("spp_junit_test_failed"))
//            )
//        ).onComplete {
//            if (it.succeeded()) {
//                val subscriptionId = it.result().subscriptionId!!
//                vertx.eventBus().consumer(toLiveViewSubscription(subscriptionId)) {
//                    val viewEvent = LiveViewEvent(it.body())
//                    val metricsData = JsonObject(viewEvent.metricsData)
//                    val value = metricsData.getJsonObject("value")
//                    val failCount = value.getInteger(testName)
//                    inlay.configuration.virtualText!!.updateVirtualText(RichText().apply {
//                        append(" Fail count: $failCount ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
//                    })
//                }
//                inlay.addEventListener {
//                    if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
//                        viewService.removeLiveView(subscriptionId)
//                    }
//                }
//            } else {
//                show(it.cause().message!!)
//            }
//        }
    }
}

registerIndicator(TestStabilityIndicator(project))
