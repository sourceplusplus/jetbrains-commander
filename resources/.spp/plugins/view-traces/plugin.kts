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
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.marker.service.ArtifactScopeService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG
import spp.plugin.*
import spp.protocol.platform.auth.RolePermission

/**
 * Opens the 'Endpoint-Traces' dashboard via portal popup.
 */
class ViewTracesCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_traces")
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_view") + " ➛ " + message("traces") + " ➛ " + message("scope") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("method") +
            "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        val detectedEndpoints = context.guideMark?.getUserData(EndpointDetector.DETECTED_ENDPOINTS) ?: return
        val endpointId = detectedEndpoints.firstNotNullOfOrNull { it.id } ?: return
        val serviceId = endpointId.substringBefore("_")
        val pageType = "Traces"
        val newPage = "/dashboard/GENERAL/Endpoint/$serviceId/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.guideMark!!.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.guideMark!!.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        if (!selfInfo.permissions.contains(RolePermission.VIEW_TRACES)) {
            return false
        }

        return ArtifactScopeService.isOnOrInsideFunction(context.qualifiedName, context.element)
    }
}

registerCommand(ViewTracesCommand(project))
