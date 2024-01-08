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
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.jetbrains.status.SourceStatusService
import spp.jetbrains.view.manager.LiveViewChartManager
import spp.plugin.*

/**
 * Opens the 'Endpoint-Overview' dashboard via portal popup.
 */
class ViewOverviewCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_overview")
    override fun getDescription() = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_view") + " ➛ " + message("overview") + " ➛ " + message("scope") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("service") +
            "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        LiveViewChartManager.getInstance(project).showOverviewActivity()
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        return SourceStatusService.getInstance(project).isReady()
    }
}

registerCommand(ViewOverviewCommand(project))
