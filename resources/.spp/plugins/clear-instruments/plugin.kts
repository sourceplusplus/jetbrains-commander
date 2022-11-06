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
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.plugin.*
import spp.protocol.platform.auth.RolePermission

class ClearInstrumentsCommand(project: Project) : LiveCommand(project) {
    override val name = "Clear Instruments"
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_instrument") + " ➛ " + message("clear_all") + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        instrumentService!!.clearAllLiveInstruments(null).onComplete {
            if (it.succeeded()) {
                show("Successfully cleared active live instrument(s)")
            } else {
                show(it.cause().message, notificationType = ERROR)
            }
        }
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.REMOVE_LIVE_INSTRUMENT)) {
            return false
        }

        return instrumentService != null
    }
}

registerCommand(ClearInstrumentsCommand(project))
