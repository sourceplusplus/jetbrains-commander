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
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.project.Project
import io.vertx.core.json.JsonArray
import io.vertx.kotlin.coroutines.await
import liveplugin.PluginUtil.*
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.artifact.service.ArtifactTypeService
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.plugin.*

class LibraryCheckCommand(project: Project) : LiveCommand(project) {
    override val name = "Library Check"
    override val params: List<String> = listOf("Library Name") //todo: optional params
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "Find all jar libraries used in the currently active services" + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val librarySearch = (context.args.firstOrNull() ?: "").lowercase()
        val foundLibraries = mutableSetOf<String>()

        val activeServices = managementService.getServices().await()
        if (activeServices.isEmpty()) {
            show("Unable to find active services", notificationType = ERROR)
            return
        }
        val activeServiceInstances = activeServices.flatMap { managementService.getInstances(it).await() }
        if (activeServiceInstances.isEmpty()) {
            show("Unable to find active service instances", notificationType = ERROR)
            return
        }

        activeServiceInstances.forEach {
            val jarDependencies = it.attributes.entries.find { it.key == "Jar Dependencies" }?.let { JsonArray(it.value) }
            jarDependencies?.let {
                foundLibraries.addAll(
                    jarDependencies.list.map { it.toString() }
                        .filter { it.lowercase().contains(librarySearch) }
                )
            }
        }

        val serviceCount = activeServices.size
        val instanceCount = activeServiceInstances.size
        showInConsole(
            foundLibraries,
            "Libraries Found (Services: $serviceCount - Instances: $instanceCount)",
            project
        )
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        return ArtifactTypeService.isJvm(context.element)
    }
}

registerCommand(LibraryCheckCommand(project))
