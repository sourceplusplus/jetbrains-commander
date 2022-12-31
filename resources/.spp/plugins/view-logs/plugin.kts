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
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import io.vertx.core.json.JsonObject
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.plugin.LiveViewLogService
import spp.jetbrains.status.SourceStatusService
import spp.plugin.*
import spp.protocol.artifact.ArtifactNameUtils
import spp.protocol.artifact.log.Log
import spp.protocol.platform.auth.RolePermission
import spp.protocol.service.SourceServices
import spp.protocol.view.LiveView
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent
import java.awt.Font
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tails live application logs and displays them in console.
 */
class ViewLogsCommand(
    project: Project,
    override val name: String = message("view_logs")
) : LiveCommand(project) {

    private val liveOutputType = ConsoleViewContentType(
        "LIVE_OUTPUT",
        TextAttributes(
            LookupCellRenderer.MATCHED_FOREGROUND_COLOR, null, null, null, Font.PLAIN
        )
    )

    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_view") + " ➛ " + message("logs") + " ➛ " + message("scope") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("method") +
            "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        viewService.addLiveView(
            LiveView(
                entityIds = mutableSetOf("*"),
                viewConfig = LiveViewConfig("view-logs-command", listOf("endpoint_logs"))
            )
        ).onSuccess { sub ->
            val console = LiveViewLogService.getInstance(project).showInConsole("", "Service Logs", project)

            val consumer = vertx.eventBus().consumer<JsonObject>(
                SourceServices.Subscribe.toLiveViewSubscriberAddress("system")
            )
            consumer.handler {
                val liveViewEvent = LiveViewEvent(it.body())
                if (liveViewEvent.subscriptionId != sub.subscriptionId) return@handler

                val rawLog = Log(JsonObject(liveViewEvent.metricsData).getJsonObject("log"))
                val localTime = LocalTime.ofInstant(rawLog.timestamp, ZoneId.systemDefault())
                val logLine = buildString {
                    append(localTime)
                    append(" [").append(rawLog.thread).append("] ")
                    append(rawLog.level.uppercase()).append(" - ")
                    rawLog.logger?.let { append(ArtifactNameUtils.getShortQualifiedClassName(it)).append(" - ") }
                    append(rawLog.toFormattedMessage())
                    appendLine()
                }

                when (rawLog.level.uppercase()) {
                    "LIVE" -> console.print(logLine, liveOutputType)
                    "WARN", "ERROR" -> console.print(logLine, ConsoleViewContentType.ERROR_OUTPUT)
                    else -> console.print(logLine, ConsoleViewContentType.NORMAL_OUTPUT)
                }
            }

            console.whenDisposed {
                consumer.unregister()
                viewService.removeLiveView(sub.subscriptionId!!)
            }
        }.onFailure {
            show(it.message, notificationType = NotificationType.ERROR)
        }
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.VIEW_LOGS)) {
            return false
        }

        return SourceStatusService.getInstance(project).isReady()
    }
}

registerCommand(ViewLogsCommand(project))
