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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import spp.jetbrains.marker.indicator.LiveIndicator
import spp.jetbrains.marker.service.ArtifactCreationService
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.api.ExpressionSourceMark
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.gutter.GutterMark
import spp.plugin.findIcon
import spp.plugin.registerIndicator

class UnusedEndpointIndicator(project: Project) : LiveIndicator(project) {

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)
    private val unusedIndicators = hashMapOf<GuideMark, GutterMark>()

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (event.eventCode == MARK_USER_DATA_UPDATED && EndpointDetector.ENDPOINT_FOUND != event.params.firstOrNull()) {
            return //ignore other user data updates
        }
        val endpointName = guideMark.getUserData(EndpointDetector.DETECTED_ENDPOINTS)
            ?.firstNotNullOf { it.name } ?: return

        if (event.params[1] as Boolean) {
            ApplicationManager.getApplication().runReadAction {
                val gutterMark = unusedIndicators.remove(guideMark) ?: return@runReadAction
                log.info("Removing unused endpoint indicator for: $endpointName")
                gutterMark.sourceFileMarker.removeSourceMark(gutterMark, autoRefresh = true)
            }
        } else {
            ApplicationManager.getApplication().runReadAction {
                log.info("Adding unused endpoint indicator for: $endpointName")
                val gutterMark = when (guideMark) {
                    is MethodSourceMark -> ArtifactCreationService.createMethodGutterMark(guideMark, false)
                    is ExpressionSourceMark -> ArtifactCreationService.createExpressionGutterMark(guideMark, false)
                    else -> throw IllegalStateException("Guide mark is not a method or expression")
                }
                gutterMark.configuration.activateOnMouseHover = false
                gutterMark.configuration.tooltipText = { "No data found for endpoint: $endpointName" }
                gutterMark.configuration.icon = findIcon("icons/unused-endpoint.svg")
                gutterMark.apply(true)
                unusedIndicators[guideMark] = gutterMark

                gutterMark.addEventListener {
                    if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                        unusedIndicators.remove(guideMark)
                    }
                }
            }
        }
    }
}

registerIndicator(UnusedEndpointIndicator(project))
