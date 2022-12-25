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
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import spp.jetbrains.PluginBundle
import spp.jetbrains.PluginUI
import spp.jetbrains.indicator.LiveIndicator
import spp.jetbrains.marker.SourceMarkerKeys.FUNCTION_DURATION
import spp.jetbrains.marker.SourceMarkerKeys.FUNCTION_DURATION_PREDICTION
import spp.jetbrains.marker.service.ArtifactCreationService
import spp.jetbrains.marker.source.mark.api.MethodSourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.MARK_USER_DATA_UPDATED
import spp.jetbrains.marker.source.mark.api.key.SourceKey
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.marker.source.mark.inlay.InlayMark
import spp.jetbrains.marker.source.mark.inlay.config.InlayMarkVirtualText
import spp.plugin.*
import spp.protocol.utils.toPrettyDuration
import java.awt.Color

class RuntimeDurationPredictionIndicator(project: Project) : LiveIndicator(project) {

    companion object {
        private val inlayForegroundColor = JBColor(Color.decode("#787878"), Color.decode("#787878"))
        private val INLAY = SourceKey<InlayMark>(this::class.simpleName + "_INLAY")
    }

    override val listenForEvents = listOf(MARK_USER_DATA_UPDATED)
    private val listenForInsights = setOf(FUNCTION_DURATION, FUNCTION_DURATION_PREDICTION)

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (!listenForInsights.contains(event.params.firstOrNull())) return
        synchronized(INLAY) {
            displayRuntimeDurationPredictionIndicator(guideMark)
        }
    }

    private fun displayRuntimeDurationPredictionIndicator(guideMark: GuideMark) {
        val durationInsight = guideMark.getUserData(FUNCTION_DURATION)
        val predictionInsight = guideMark.getUserData(FUNCTION_DURATION_PREDICTION)
        if (durationInsight == null && predictionInsight == null) {
            return //nothing to display
        }

        val inlay = guideMark.getUserData(INLAY) ?: ApplicationManager.getApplication().runReadAction(Computable {
            when {
                guideMark.isMethodMark -> ArtifactCreationService.createMethodInlayMark(
                    guideMark.sourceFileMarker,
                    (guideMark as MethodSourceMark).getNameIdentifier(),
                    false
                )

                guideMark.isExpressionMark -> ArtifactCreationService.createExpressionInlayMark(
                    guideMark.sourceFileMarker,
                    guideMark.lineNumber,
                    false
                )

                else -> throw IllegalStateException("Guide mark is not a method or expression")
            }
        })
        if (guideMark.getUserData(INLAY) == null) {
            guideMark.putUserData(INLAY, inlay)
            setupVirtualTextInlay(inlay)
        }

        if (durationInsight != null && predictionInsight != null) {
            val realDuration = durationInsight.value
            val predictedDuration = predictionInsight.value

            if (predictedDuration > realDuration) {
                val durationIncrease = (predictedDuration - realDuration).toPrettyDuration()
                inlay.configuration.virtualText!!.iconLocation.location = java.awt.Point(0, 3)
                inlay.configuration.virtualText!!.icon = findIcon("icons/speed-decrease.svg")
                inlay.configuration.virtualText!!.updateVirtualText(RichText().apply {
                    append("Speed decrease: ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("+$durationIncrease", SimpleTextAttributes.fromTextAttributes(TextAttributes().apply {
                        foregroundColor = Color.decode("#db5c5c")
                    }))
                    append(" [Total: ${predictedDuration.toPrettyDuration()}]", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                })
                log.info("Set expected duration to +$durationIncrease on ${guideMark.artifactQualifiedName}")
            } else if (realDuration > predictedDuration) {
                val durationDecrease = (realDuration - predictedDuration).toPrettyDuration()
                inlay.configuration.virtualText!!.iconLocation.location = java.awt.Point(0, 3)
                inlay.configuration.virtualText!!.icon = findIcon("icons/speed-increase.svg")
                inlay.configuration.virtualText!!.updateVirtualText(RichText().apply {
                    append("Speed increase: ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("-$durationDecrease", SimpleTextAttributes.fromTextAttributes(TextAttributes().apply {
                        foregroundColor = Color.decode("#629755")
                    }))
                    append(" [Total: ${predictedDuration.toPrettyDuration()}]", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                })
                log.info("Set expected duration to -$durationDecrease on ${guideMark.artifactQualifiedName}")
            } else {
                //no change
                inlay.dispose()
                guideMark.removeUserData(INLAY)
            }
        }
//        else if (predictionInsight != null) {
//            //todo: don't show unless method is completely new (not just vcs modified)
//            val predictedDuration = predictionInsight.value.toPrettyDuration()
//            inlay.configuration.virtualText!!.iconLocation.location = java.awt.Point(0, 5)
//            inlay.configuration.virtualText!!.icon = findIcon("icons/speed-prediction.svg")
//            inlay.configuration.virtualText!!.updateVirtualText(RichText().apply {
//                append("Speed prediction: ~$predictedDuration", SimpleTextAttributes.REGULAR_ATTRIBUTES)
//            })
//            log.info("Set expected duration to $predictedDuration on ${guideMark.artifactQualifiedName}")
//        }
        else {
            //no change
            inlay.dispose()
            guideMark.removeUserData(INLAY)
        }
    }

    private fun setupVirtualTextInlay(inlay: InlayMark) {
        log.info("Setting up virtual text inlay for $inlay")
        inlay.configuration.virtualText = InlayMarkVirtualText(inlay, "").apply {
            textAttributes.foregroundColor = inlayForegroundColor
            relativeFontSize = true
            if (PluginBundle.LOCALE.language == "zh") {
                font = PluginUI.MICROSOFT_YAHEI_PLAIN_14
                xOffset = 20
                fontSize = -3.5f
            } else {
                xOffset = 5
                fontSize = -1.5f
            }
        }
        inlay.configuration.virtualText!!.priority = -10_000 //make sure it's always on top
        inlay.configuration.activateOnMouseClick = false
        inlay.apply(true)
    }
}

registerIndicator(RuntimeDurationPredictionIndicator(project))
