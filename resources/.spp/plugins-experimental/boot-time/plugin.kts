import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import spp.command.LiveCommand
import spp.command.LiveCommandContext
import spp.jetbrains.sourcemarker.PluginUI.getCommandTypeColor
import spp.plugin.*
import java.time.*
import java.time.format.DateTimeFormatter

class BootTimeCommand(project: Project) : LiveCommand(project) {

    override val name = "boot-time"
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            "Gets the earliest boot time for the current service" + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val serverTimezone = skywalkingMonitorService.getTimeInfo().timezone
        if (serverTimezone == null) {
            show("Unable to determine server timezone", notificationType = NotificationType.ERROR)
            return
        }
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.ofHours(serverTimezone.toInt()))

        var startTime: LocalDateTime? = null
        skywalkingMonitorService.getActiveServices().forEach {
            skywalkingMonitorService.getServiceInstances(it.id).forEach {
                val instanceStartTime = it.attributes.find { it.name == "Start Time" }?.let {
                    ZonedDateTime.parse(it.value, timeFormatter).withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime()
                }
                if (startTime == null || instanceStartTime?.isBefore(startTime) == true) {
                    startTime = instanceStartTime
                }
            }
        }

        if (startTime != null) {
            val duration = Duration.between(startTime, LocalDateTime.now())
            val prettyTimeAgo = String.format(
                "%d days, %d hours, %d minutes, %d seconds ago",
                duration.toDays(), duration.toHours() % 24, duration.toMinutes() % 60, duration.toSeconds() % 60
            )
            show("$prettyTimeAgo ($startTime)")
        } else {
            show("Unable to find active service(s)", notificationType = NotificationType.ERROR)
        }
    }
}

registerCommand(BootTimeCommand(project))
