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
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.artifact.service.ArtifactScopeService
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.view.manager.LiveViewChartManager
import spp.plugin.*
import spp.protocol.platform.auth.RolePermission
import spp.protocol.platform.general.ServiceEndpoint

/**
 * Opens the 'Endpoint-Activity' dashboard via portal popup.
 */
class ViewActivityCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_activity")
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_view") + " ➛ " + message("activity") + " ➛ " + message("scope") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("endpoint") +
            "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        val detectedEndpoints = context.guideMark?.getUserData(EndpointDetector.DETECTED_ENDPOINTS) ?: return
        val detectedEndpoint = detectedEndpoints.find { it.id != null } ?: return
        val endpoint = ServiceEndpoint(detectedEndpoint.id!!, detectedEndpoint.name)

        LiveViewChartManager.getInstance(project).showEndpointActivity(endpoint)
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.VIEW_ACTIVITY)) {
            return false
        } else if (!ArtifactScopeService.isOnOrInsideFunction(context.qualifiedName, context.element)) {
            return false
        }

        val functionGuideMark = context.getFunctionGuideMark() ?: return false
        val detectedEndpoints = functionGuideMark.getUserData(EndpointDetector.DETECTED_ENDPOINTS)
        return (detectedEndpoints?.firstNotNullOfOrNull { it.id } != null)
    }
}

registerCommand(ViewActivityCommand(project))
