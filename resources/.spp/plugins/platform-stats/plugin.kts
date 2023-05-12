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
import liveplugin.PluginUtil.showInConsole
import liveplugin.implementation.Console
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.*
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.plugin.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class PlatformStatsCommand(project: Project) : LiveCommand(project) {
    override val name = "Platform Stats"
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "Displays Source++ platform stats" + "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        managementService.getStats().onSuccess {
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
