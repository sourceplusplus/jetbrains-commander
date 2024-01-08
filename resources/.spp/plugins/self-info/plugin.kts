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
import liveplugin.PluginUtil.showInConsole
import liveplugin.implementation.Console
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.plugin.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SelfInfoCommand(project: Project) : LiveCommand(project) {
    override val name = "Self Info"
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "Displays current developer information" + "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        val formattedSelfInfo = StringBuilder()
        formattedSelfInfo.append("Developer:").append(" ").append(selfInfo.developer.id).appendLine()
        if (selfInfo.roles.isNotEmpty()) {
            formattedSelfInfo.append("Roles:")
            selfInfo.roles.sortedBy { it.roleName }.forEach {
                formattedSelfInfo.appendLine().append(" - ").append(it.roleName)
            }
        }

        if (selfInfo.permissions.isNotEmpty()) {
            formattedSelfInfo.appendLine().append("Permissions:")
            selfInfo.permissions.sortedBy { it.name }.forEach {
                formattedSelfInfo.appendLine().append(" - ").append(it.name)
            }
        }

        if (selfInfo.access.isNotEmpty()) {
            formattedSelfInfo.appendLine().append("Access:")
            selfInfo.access.sortedBy { it.id }.forEach {
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
    }
}

registerCommand(SelfInfoCommand(project))
