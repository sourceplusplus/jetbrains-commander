import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.marker.impl.ArtifactCreationService
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.inlay.config.InlayMarkVirtualText
import spp.plugin.*
import spp.protocol.SourceServices.Provide.toLiveInstrumentSubscriberAddress
import spp.protocol.artifact.ArtifactNameUtils
import spp.protocol.instrument.LiveBreakpoint
import spp.protocol.instrument.LiveSourceLocation
import spp.protocol.instrument.event.LiveInstrumentEvent
import spp.protocol.instrument.event.LiveInstrumentEventType.BREAKPOINT_HIT
import spp.protocol.instrument.throttle.InstrumentThrottle
import spp.protocol.instrument.throttle.ThrottleStep.SECOND
import spp.protocol.marshall.ProtocolMarshaller.deserializeLiveBreakpointHit
import spp.protocol.platform.developer.SelfInfo
import java.awt.Color

class WatchVariableCommand(project: Project) : LiveCommand(project) {
    override val name = "watch-variable"
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "Adds live breakpoint to display the variable's current value" + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val variableName = context.variableName
        if (variableName == null) {
            show("Unable to determine variable name", notificationType = ERROR)
            return
        }
        val selfId = selfInfo.developer.id

        instrumentService!!.addLiveInstrument(
            LiveBreakpoint(
                LiveSourceLocation(
                    ArtifactNameUtils.getQualifiedClassName(context.artifactQualifiedName.identifier)!!,
                    context.lineNumber + 1
                ),
                throttle = InstrumentThrottle(1, SECOND),
                hitLimit = -1
            )
        ).onComplete {
            if (it.succeeded()) {
                runReadAction { addInlay(context, selfId, it.result().id!!, variableName) }
            } else {
                show(it.cause().message, notificationType = ERROR)
            }
        }
    }

    private fun addInlay(context: LiveCommandContext, selfId: String, instrumentId: String, variableName: String?) {
        val inlay = ArtifactCreationService.createExpressionInlayMark(context.fileMarker, context.lineNumber, false)
        val virtualText = InlayMarkVirtualText(inlay, " // Live value: n/a")
        virtualText.useInlinePresentation = true
        virtualText.textAttributes.foregroundColor = Color.orange
        inlay.configuration.activateOnMouseClick = false
        inlay.configuration.virtualText = virtualText
        inlay.apply(true)

        val consumer = vertx.eventBus().consumer<JsonObject>(toLiveInstrumentSubscriberAddress(selfId))
        inlay.addEventListener {
            if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                show("Removed live instrument for watched variable: $variableName")
                instrumentService!!.removeLiveInstrument(instrumentId)
                consumer.unregister()
            }
        }

        consumer.handler {
            val liveEvent = Json.decodeValue(it.body().toString(), LiveInstrumentEvent::class.java)
            if (liveEvent.eventType == BREAKPOINT_HIT) {
                val bpHit = deserializeLiveBreakpointHit(JsonObject(liveEvent.data))
                if (bpHit.breakpointId == instrumentId) {
                    val liveVariables = bpHit.stackTrace.first().variables
                    val liveVar = liveVariables.find { it.name == variableName }
                    if (liveVar != null) {
                        virtualText.updateVirtualText(" // Live value: " + liveVar.value)
                    }
                }
            }
        }
    }

    override fun isAvailable(selfInfo: SelfInfo, context: LiveLocationContext): Boolean {
        return instrumentService != null
    }
}

registerCommand(WatchVariableCommand(project))
