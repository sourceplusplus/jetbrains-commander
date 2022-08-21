import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import liveplugin.PluginUtil.showInConsole
import liveplugin.implementation.Console
import spp.command.LiveCommand
import spp.command.LiveCommandContext
import spp.jetbrains.sourcemarker.PluginUI.getCommandTypeColor
import spp.plugin.registerCommand
import spp.plugin.show
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class PlatformStatsCommand(project: Project) : LiveCommand(project) {
    override val name = "Platform Stats"
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            "Displays Source++ platform stats" + "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        liveService.getStats().onSuccess {
            val formattedStats = StringBuilder()
            formattedStats.append("Connected markers: ")
                .append(it.getJsonObject("platform").getInteger("connected-markers")).append("\n")
            formattedStats.append("Available services:")
            it.getJsonObject("platform").getJsonObject("services").getJsonObject("core").map.forEach {
                if (it.value is Number && (it.value as Number).toInt() > 0) {
                    formattedStats.append("\n").append(" - ").append(it.key).append(" (").append(it.value).append(")")
                }
            }

            formattedStats.append("\n\n").append("Connected probes: ")
                .append(it.getJsonObject("platform").getInteger("connected-probes")).append("\n")
            formattedStats.append("Available services:")
            it.getJsonObject("platform").getJsonObject("services").getJsonObject("probe").map.forEach {
                if (it.value is Number && (it.value as Number).toInt() > 0) {
                    formattedStats.append("\n").append(" - ").append(it.key).append(" (").append(it.value).append(")")
                }
            }

            val localTime = LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            showInConsole(
                formattedStats,
                "Platform Stats - $localTime",
                project,
                Console.guessContentTypeOf(formattedStats),
                0
            )
        }.onFailure {
            show(it.message, "Error", NotificationType.ERROR)
        }
    }
}

registerCommand(PlatformStatsCommand(project))
