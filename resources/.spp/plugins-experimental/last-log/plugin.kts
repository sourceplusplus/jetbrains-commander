import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.JBColor
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import spp.indicator.LiveIndicator
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.jvm.psi.LoggerDetector
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.inlay.config.InlayMarkVirtualText
import spp.jetbrains.sourcemarker.SourceMarkerPlugin.vertx
import spp.jetbrains.sourcemarker.mark.SourceMarkKeys
import spp.plugin.registerIndicator
import spp.protocol.SourceServices.Provide.toLiveViewSubscriberAddress
import spp.protocol.artifact.ArtifactQualifiedName
import spp.protocol.artifact.ArtifactType
import spp.protocol.artifact.log.Log
import spp.protocol.instrument.LiveSourceLocation
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent
import spp.protocol.view.LiveViewSubscription
import java.awt.Color

class LastLogIndicator : LiveIndicator() {
    companion object {
        private val log = logger<LastLogIndicator>()
        private val inlayForegroundColor = JBColor(Color.decode("#787878"), Color.decode("#787878"))
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (event.eventCode == MARK_USER_DATA_UPDATED && SourceMarkKeys.LOGGER_DETECTOR == event.params.firstOrNull()) {
            val loggerDetector = event.params.get(1) as LoggerDetector
            if (guideMark is MethodSourceMark) {
                loggerDetector.getOrFindLoggerStatements(guideMark)
            }
            return
        } else if (event.eventCode == MARK_USER_DATA_UPDATED && LoggerDetector.LOGGER_STATEMENTS != event.params.firstOrNull()) {
            return //ignore other user data updates
        }
        val detectedLogPatterns = event.params.get(1) as List<LoggerDetector.DetectedLogger>

        ApplicationManager.getApplication().runReadAction {
            detectedLogPatterns.forEach {
                addInlay(guideMark, it)
            }
        }
    }

    private fun addInlay(guideMark: GuideMark, detectedLog: LoggerDetector.DetectedLogger) {
        val inlayMark = ArtifactCreationService.createExpressionInlayMark(
            guideMark.sourceFileMarker,
            detectedLog.lineLocation,
            false
        )
        liveViewService.addLiveViewSubscription(
            LiveViewSubscription(
                null,
                listOf(detectedLog.logPattern),
                ArtifactQualifiedName(
                    inlayMark.artifactQualifiedName.identifier,
                    lineNumber = inlayMark.artifactQualifiedName.lineNumber,
                    type = ArtifactType.EXPRESSION
                ),
                LiveSourceLocation(
                    inlayMark.artifactQualifiedName.identifier,
                    line = inlayMark.artifactQualifiedName.lineNumber!!
                ),
                LiveViewConfig("LOGS", listOf("endpoint_logs"))
            )
        ).onComplete {
            if (it.succeeded()) {
                val virtualText = InlayMarkVirtualText(inlayMark, " Waiting for logs...")
                virtualText.useInlinePresentation = true
                virtualText.textAttributes.foregroundColor = inlayForegroundColor
                virtualText.fontSize = -0.5f
                virtualText.relativeFontSize = true
                inlayMark.configuration.activateOnMouseClick = false
                inlayMark.configuration.virtualText = virtualText
                inlayMark.apply(true)

                val subscriptionId = it.result().subscriptionId!!
                val consumer = vertx.eventBus().consumer<JsonObject>(toLiveViewSubscriberAddress(subscriptionId))
                consumer.handler {
                    val liveViewEvent = Json.decodeValue(it.body().toString(), LiveViewEvent::class.java)
                    val rawMetrics = JsonObject(liveViewEvent.metricsData)
                    val logData = Json.decodeValue(rawMetrics.getJsonObject("log").toString(), Log::class.java)
                    virtualText.updateVirtualText(" [" + logData.arguments.joinToString(",") + "] @ " + logData.timestamp)
                }
                inlayMark.addEventListener { event ->
                    if (event.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                        liveViewService.removeLiveViewSubscription(subscriptionId)
                        consumer.unregister()
                    }
                }
            } else {
                log.error("Failed to add live view subscription", it.cause())
            }
        }
    }
}

registerIndicator(LastLogIndicator())
