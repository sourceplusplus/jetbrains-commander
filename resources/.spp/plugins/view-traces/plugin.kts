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
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.JsonObject
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.artifact.service.ArtifactScopeService
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.view.LiveViewTraceManager
import spp.jetbrains.view.window.LiveTraceWindow
import spp.plugin.*
import spp.protocol.artifact.trace.Trace
import spp.protocol.platform.auth.RolePermission
import spp.protocol.service.SourceServices.Subscribe.toLiveViewSubscriberAddress
import spp.protocol.view.LiveView
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent

/**
 * Opens the 'Endpoint-Traces' dashboard via portal popup.
 */
class ViewTracesCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_traces")
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_view") + " ➛ " + message("traces") + " ➛ " + message("scope") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("method") +
            "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        val detectedEndpoints = context.guideMark?.getUserData(EndpointDetector.DETECTED_ENDPOINTS) ?: return
        detectedEndpoints.firstNotNullOfOrNull { it.id } ?: return

        val refreshRate = 2000
        val endpointName = detectedEndpoints.first().name
        viewService.addLiveView(
            LiveView(
                entityIds = mutableSetOf(endpointName),
                viewConfig = LiveViewConfig("TRACE_VIEW", listOf("endpoint_traces"), refreshRate)
            )
        ).onSuccess { liveView ->
            LiveViewTraceManager.getInstance(project).showEndpointTraces(liveView, endpointName) {
                consumerCreator(liveView, it)
            }
        }.onFailure {
            show(it.message, notificationType = NotificationType.ERROR)
        }
    }

    private fun consumerCreator(liveView: LiveView, traceWindow: LiveTraceWindow): MessageConsumer<JsonObject> {
        val consumer = vertx.eventBus().consumer<JsonObject>(toLiveViewSubscriberAddress("system"))
        consumer.handler {
            val liveViewEvent = LiveViewEvent(it.body())
            if (liveView.subscriptionId != liveViewEvent.subscriptionId) return@handler

            val event = JsonObject(liveViewEvent.metricsData)
            val trace = Trace(event.getJsonObject("trace"))
            traceWindow.addTrace(trace)
        }
        return consumer
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.VIEW_TRACES)) {
            return false
        }

        return ArtifactScopeService.isOnOrInsideFunction(context.qualifiedName, context.element)
    }
}

registerCommand(ViewTracesCommand(project))
