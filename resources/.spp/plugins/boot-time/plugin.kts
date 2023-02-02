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
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.vertx.kotlin.coroutines.await
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.plugin.*
import java.time.*
import java.time.format.DateTimeFormatter

class BootTimeCommand(project: Project) : LiveCommand(project) {
    override val name = "Boot Time"
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "Gets the earliest boot time for the current service" + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val serverTimezone = managementService.getTimeInfo().await().timezone
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.ofHours(serverTimezone.toInt()))

        var startTime: LocalDateTime? = null
        managementService.getServices().await().forEach {
            managementService.getInstances(it.id).await().forEach {
                val instanceStartTime = it.attributes.entries.find { it.key == "Start Time" }?.let {
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
