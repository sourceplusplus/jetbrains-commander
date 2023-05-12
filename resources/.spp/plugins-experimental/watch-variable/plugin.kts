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
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import io.vertx.core.json.JsonObject
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.jetbrains.marker.service.ArtifactCreationService
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.inlay.config.InlayMarkVirtualText
import spp.plugin.*
import spp.protocol.artifact.ArtifactNameUtils
import spp.protocol.instrument.LiveBreakpoint
import spp.protocol.instrument.event.LiveBreakpointHit
import spp.protocol.instrument.event.LiveInstrumentEvent
import spp.protocol.instrument.event.LiveInstrumentEventType.BREAKPOINT_HIT
import spp.protocol.instrument.location.LiveSourceLocation
import spp.protocol.instrument.throttle.InstrumentThrottle
import spp.protocol.instrument.throttle.ThrottleStep.SECOND
import spp.protocol.service.SourceServices.Subscribe.toLiveInstrumentSubscriberAddress
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
                location = LiveSourceLocation(
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
            val liveEvent = LiveInstrumentEvent(it.body())
            if (liveEvent.eventType == BREAKPOINT_HIT) {
                val bpHit = LiveBreakpointHit(JsonObject(liveEvent.data))
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

    override fun isAvailable(context: LiveLocationContext): Boolean {
        return instrumentService != null
    }
}

registerCommand(WatchVariableCommand(project))
