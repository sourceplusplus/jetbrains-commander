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
import com.intellij.util.containers.isNullOrEmpty
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.JsonObject
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.jetbrains.marker.SourceMarker
import spp.jetbrains.marker.source.info.LoggerDetector.Companion.DETECTED_LOGGER
import spp.jetbrains.marker.source.mark.guide.ClassGuideMark
import spp.jetbrains.marker.source.mark.guide.ExpressionGuideMark
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.guide.MethodGuideMark
import spp.jetbrains.view.manager.LiveViewLogManager
import spp.jetbrains.view.window.LiveLogWindow
import spp.plugin.registerCommand
import spp.protocol.artifact.ArtifactNameUtils
import spp.protocol.artifact.ArtifactQualifiedName
import spp.protocol.service.SourceServices.Subscribe.toLiveViewSubscriberAddress
import spp.protocol.view.LiveView
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent

/**
 * Tails live application logs and displays them in console.
 * Can be triggered on individual log statements or on classes or methods containing log statements.
 */
class TailLogsCommand(
    project: Project,
    override val name: String = "Tail Logs"
) : LiveCommand(project) {

    override fun getDescription(context: LiveLocationContext): String {
        return when (val guideMark = getLoggerGuideMark(context.fileMarker.project, context.qualifiedName)) {
            is ClassGuideMark -> buildString {
                append("<html><span style=\"color: ").append(commandTypeColor).append("\">")
                append("Tail logs produced this code. Scope:")
                append("</span> </span><span style=\"color: ").append(commandHighlightColor).append("\">")
                append(message("class"))
                append("</span></html>")
            }

            is MethodGuideMark -> buildString {
                append("<html><span style=\"color: ").append(commandTypeColor).append("\">")
                append("Tail logs produced this code. Scope:")
                append("</span> </span><span style=\"color: ").append(commandHighlightColor).append("\">")
                append(message("method"))
                append("</span></html>")
            }

            is ExpressionGuideMark -> buildString {
                append("<html><span style=\"color: ").append(commandTypeColor).append("\">")
                append("Tail logs produced this code. Scope:")
                append("</span> </span><span style=\"color: ").append(commandHighlightColor).append("\">")
                append(message("statement"))
                append("</span></html>")
            }

            else -> error("Unexpected guide mark type: $guideMark")
        }
    }

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val service = statusService.getCurrentService()
        if (service == null) {
            log.warn("No service selected, ignoring tail logs command")
            return
        }

        val guideMark = getLoggerGuideMark(context.fileMarker.project, context.artifactQualifiedName)!!
        val loggerStatements = when (guideMark) {
            is ExpressionGuideMark -> listOf(guideMark.getUserData(DETECTED_LOGGER)!!)
            else -> guideMark.getChildren().mapNotNull { it.getUserData(DETECTED_LOGGER) }
        }
        log.info("Tailing logs for statements: ${loggerStatements.map { it.logPattern }}")

        val refreshRate = 1000
        val liveView = LiveView(
            entityIds = loggerStatements.map { it.logPattern }.toMutableSet(),
            viewConfig = LiveViewConfig(
                "tail-logs-command",
                listOf("endpoint_logs"),
                refreshRateLimit = refreshRate
            )
        )
        LiveViewLogManager.getInstance(project).getOrCreateLogWindow(
            liveView,
            { consumerCreator(it) },
            "Tail: ${ArtifactNameUtils.removePackageAndClassName(guideMark.artifactQualifiedName.identifier)}"
        )
    }

    private fun consumerCreator(
        logWindow: LiveLogWindow
    ): MessageConsumer<JsonObject> {
        val consumer = vertx.eventBus().consumer<JsonObject>(
            toLiveViewSubscriberAddress("system")
        )
        consumer.handler {
            val liveViewEvent = LiveViewEvent(it.body())
            if (liveViewEvent.subscriptionId != logWindow.liveView.subscriptionId) return@handler

            logWindow.handleEvent(liveViewEvent)
        }
        return consumer
    }

    //todo: ability to dynamically recheck isAvailable while control bar is open
    override fun isAvailable(context: LiveLocationContext): Boolean {
        return getLoggerGuideMark(context.fileMarker.project, context.qualifiedName) != null
    }

    private fun getLoggerGuideMark(project: Project, artifactQualifiedName: ArtifactQualifiedName): GuideMark? {
        var qualifiedName: ArtifactQualifiedName? = artifactQualifiedName
        var guideMark: GuideMark? = null
        do {
            if (qualifiedName == null) continue
            guideMark = SourceMarker.getInstance(project).getGuideMark(qualifiedName)

            if (guideMark is ExpressionGuideMark) {
                if (guideMark.getUserData(DETECTED_LOGGER) == null) {
                    guideMark = null
                }
            } else {
                if (guideMark?.getChildren()?.mapNotNull { it.getUserData(DETECTED_LOGGER) }.isNullOrEmpty()) {
                    guideMark = null
                }
            }
            qualifiedName = qualifiedName.asParent()
        } while (guideMark == null && qualifiedName != null)

        return guideMark
    }
}

registerCommand(TailLogsCommand(project))
