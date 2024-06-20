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
import spp.jetbrains.artifact.model.FunctionArtifact
import spp.jetbrains.artifact.service.toArtifact
import spp.jetbrains.marker.SourceMarkerUtils
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.jetbrains.view.manager.LiveViewChartManager
import spp.plugin.*
import spp.protocol.artifact.metrics.MetricType
import spp.protocol.instrument.LiveMeter

class ViewTestStabilityCommand(project: Project) : LiveCommand(project) {
    override val name = "View Test Stability"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val service = statusService.getCurrentService()
        if (service == null) {
            log.warn("No service selected")
            return
        }

        //find functions annotated with org.junit.jupiter.api.Test
        val guideMark = context.guideMark ?: return
        SourceMarkerUtils.doOnReadThread {
            val annotations = (guideMark.getPsiElement().toArtifact() as? FunctionArtifact)?.getAnnotations()
            annotations?.find { it.text == "@Test" }  //todo: type check
        } ?: return

        val testName = "public void " + guideMark.artifactQualifiedName.identifier
        log.info("Found test function: $testName")

        val formattedTestName = LiveMeter.formatMeterName(testName)
        val fullFormattedTestName = "spp_junit_test_successful_$formattedTestName"
        LiveViewChartManager.getInstance(project).showChart(
            service.id,
            "Test '$testName' Stability",
            "Service",
            listOf(MetricType(fullFormattedTestName))
        )
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        val service = statusService.getCurrentService()
        if (service == null) {
            log.warn("No service selected")
            return false
        }//todo: ensure necessary live views/instruments are already set up

        val guideMark = context.getFunctionGuideMark() ?: return false
        return SourceMarkerUtils.doOnReadThread {
            val annotations = (guideMark.getPsiElement().toArtifact() as? FunctionArtifact)?.getAnnotations()
            annotations?.any { it.text == "@Test" } ?: false  //todo: type check
        }
    }
}

registerCommand(ViewTestStabilityCommand(project))
