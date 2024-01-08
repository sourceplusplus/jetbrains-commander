/*
 * Source++, the continuous feedback platform for developers.
 * Copyright (C) 2022-2024 CodeBrig, Inc.
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
import com.intellij.openapi.project.Project
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.JsonObject
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.jetbrains.status.SourceStatusService
import spp.jetbrains.view.manager.LiveViewLogManager
import spp.jetbrains.view.window.LiveLogWindow
import spp.plugin.*
import spp.protocol.platform.auth.RolePermission
import spp.protocol.service.SourceServices.Subscribe.toLiveViewSubscriberAddress
import spp.protocol.view.LiveView
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent

/**
 * Tails live application logs and displays them in console.
 */
class ViewLogsCommand(
    project: Project,
    override val name: String = message("view_logs")
) : LiveCommand(project) {

    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_view") + " ➛ " + message("logs") + " ➛ " + message("scope") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("service") +
            "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val service = statusService.getCurrentService()
        if (service == null) {
            log.warn("No service selected")
            return
        }

        val refreshRate = 1000
        val liveView = LiveView(
            entityIds = mutableSetOf("*"),
            viewConfig = LiveViewConfig("view-logs-command", listOf("endpoint_logs"), refreshRate)
        )
        LiveViewLogManager.getInstance(project)
            .getOrCreateLogWindow(liveView, { consumerCreator(it) }, "Service: ${service.name}")
    }

    private fun consumerCreator(
        logWindow: LiveLogWindow
    ): MessageConsumer<JsonObject> {
        val consumer = vertx.eventBus().consumer<JsonObject>(toLiveViewSubscriberAddress(selfInfo.developer.id))
        consumer.handler {
            val liveViewEvent = LiveViewEvent(it.body())
            if (liveViewEvent.subscriptionId != logWindow.liveView.subscriptionId) return@handler

            logWindow.handleEvent(liveViewEvent)
        }
        return consumer
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.VIEW_LOGS)) {
            return false
        }

        return SourceStatusService.getInstance(project).isReady()
    }
}

registerCommand(ViewLogsCommand(project))
