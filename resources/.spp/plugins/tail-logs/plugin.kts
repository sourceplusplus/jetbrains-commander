import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.vertx.core.json.JsonObject
import liveplugin.PluginUtil.showInConsole
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.marker.SourceMarker
import spp.jetbrains.marker.source.info.LoggerDetector.Companion.DETECTED_LOGGER
import spp.jetbrains.marker.source.info.LoggerDetector.Companion.LOGGER_STATEMENTS
import spp.jetbrains.marker.source.mark.guide.ClassGuideMark
import spp.jetbrains.marker.source.mark.guide.ExpressionGuideMark
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.guide.MethodGuideMark
import spp.plugin.registerCommand
import spp.plugin.show
import spp.plugin.whenDisposed
import spp.protocol.SourceServices.Provide.toLiveViewSubscriberAddress
import spp.protocol.artifact.ArtifactNameUtils
import spp.protocol.artifact.ArtifactQualifiedName
import spp.protocol.artifact.log.Log
import spp.protocol.platform.developer.SelfInfo
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent
import spp.protocol.view.LiveViewSubscription
import java.time.LocalTime
import java.time.ZoneId

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

    override fun trigger(context: LiveCommandContext) {
        val guideMark = getLoggerGuideMark(context.fileMarker.project, context.artifactQualifiedName)!!
        val loggerStatements = when (guideMark) {
            is ClassGuideMark -> guideMark.getChildren().flatMap { it.getUserData(LOGGER_STATEMENTS) ?: emptyList() }
            is MethodGuideMark -> guideMark.getUserData(LOGGER_STATEMENTS)!!
            is ExpressionGuideMark -> listOf(guideMark.getUserData(DETECTED_LOGGER)!!)
            else -> error("Unexpected guide mark type: $guideMark")
        }

        viewService.addLiveViewSubscription(
            LiveViewSubscription(
                entityIds = loggerStatements.map { it.logPattern }.toMutableSet(),
                liveViewConfig = LiveViewConfig("tail-logs-command", listOf("endpoint_logs"))
            )
        ).onSuccess { sub ->
            val console = showInConsole(
                "",
                "Logs: " + ArtifactNameUtils.removePackageAndClassName(guideMark.artifactQualifiedName.identifier),
                project
            )

            val consumer = vertx.eventBus().consumer<JsonObject>(
                toLiveViewSubscriberAddress("system")
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

                if (rawLog.level == "WARN" || rawLog.level == "ERROR") {
                    console.print(logLine, ConsoleViewContentType.ERROR_OUTPUT)
                } else {
                    console.print(logLine, ConsoleViewContentType.NORMAL_OUTPUT)
                }
            }

            console.whenDisposed {
                consumer.unregister()
                viewService.removeLiveViewSubscription(sub.subscriptionId!!)
            }
        }.onFailure {
            show(it.message, notificationType = NotificationType.ERROR)
        }
    }

    //todo: ability to dynamically recheck isAvailable while control bar is open
    override fun isAvailable(selfInfo: SelfInfo, context: LiveLocationContext): Boolean {
        return getLoggerGuideMark(context.fileMarker.project, context.qualifiedName) != null
    }

    private fun getLoggerGuideMark(project: Project, artifactQualifiedName: ArtifactQualifiedName): GuideMark? {
        var qualifiedName: ArtifactQualifiedName? = artifactQualifiedName
        var guideMark: GuideMark? = null
        do {
            if (qualifiedName == null) continue
            guideMark = SourceMarker.getInstance(project).getGuideMark(qualifiedName)

            if (guideMark is ExpressionGuideMark && guideMark.getUserData(DETECTED_LOGGER) == null) {
                guideMark = null
            } else if (guideMark is MethodGuideMark && guideMark.getUserData(LOGGER_STATEMENTS).isNullOrEmpty()) {
                guideMark = null
            } else if (guideMark is ClassGuideMark) {
                if (guideMark.getChildren().flatMap { it.getUserData(LOGGER_STATEMENTS) ?: emptyList() }.isEmpty()) {
                    guideMark = null
                }
            }
            qualifiedName = qualifiedName.asParent()
        } while (guideMark == null && qualifiedName != null)

        return guideMark
    }
}

registerCommand(TailLogsCommand(project))
