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

class SelfInfoCommand(project: Project) : LiveCommand(project) {
    override val name = "Self Info"
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            "Displays current developer information" + "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        liveService.getSelf().onSuccess {
            val formattedSelfInfo = StringBuilder()
            formattedSelfInfo.append("Developer:").append(" ").append(it.developer.id).appendLine()
            if (it.roles.isNotEmpty()) {
                formattedSelfInfo.append("Roles:")
                it.roles.sortedBy { it.roleName }.forEach {
                    formattedSelfInfo.appendLine().append(" - ").append(it.roleName)
                }
            }

            if (it.permissions.isNotEmpty()) {
                formattedSelfInfo.appendLine().append("Permissions:")
                it.permissions.sortedBy { it.name }.forEach {
                    formattedSelfInfo.appendLine().append(" - ").append(it.name)
                }
            }

            if (it.access.isNotEmpty()) {
                formattedSelfInfo.appendLine().append("Access:")
                it.access.sortedBy { it.id }.forEach {
                    formattedSelfInfo.appendLine().append(" - ").append(it)
                }
            }

            val localTime = LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            showInConsole(
                formattedSelfInfo,
                "Self Info - $localTime",
                project,
                Console.guessContentTypeOf(formattedSelfInfo),
                0
            )
        }.onFailure {
            show(it.message, "Error", NotificationType.ERROR)
        }
    }
}

registerCommand(SelfInfoCommand(project))
