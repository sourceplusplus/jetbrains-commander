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
import io.vertx.kotlin.coroutines.await
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.jetbrains.status.SourceStatusService
import spp.jetbrains.view.manager.LiveViewChartManager
import spp.plugin.*
import spp.protocol.artifact.metrics.MetricType

class ViewChartCommand(
    project: Project,
    override val name: String = "View Chart"
) : LiveCommand(project) {

    override val params: List<String> = listOf("Chart Name")
    override fun getDescription() = "Show live chart for the supplied chart name"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val chartName = context.args.joinToString("_").lowercase()
        val metricType = MetricType.fromBestGuess(chartName)
        val serviceInstance = statusService.getCurrentService()?.let {
            managementService.getInstances(it).await().firstOrNull()
        } ?: return
        LiveViewChartManager.getInstance(project).showChart(
            serviceInstance.id,
            metricType.simpleName,
            "ServiceInstance",
            listOf(metricType)
        )
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        return SourceStatusService.getInstance(project).isReady()
    }
}

registerCommand(ViewChartCommand(project))
