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
import com.intellij.ide.util.DirectoryUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PsiNavigateUtil
import liveplugin.implementation.common.IdeUtil
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.plugin.*

class NewIndicatorCommand(project: Project) : LiveCommand(project) {
    override val name = message("New Indicator")
    override val params: List<String> = listOf("Indicator Name")
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "Add new custom live indicator" + "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        if (context.args.isEmpty()) {
            show("Missing indicator name", notificationType = NotificationType.ERROR)
            return
        }

        runWriteAction {
            val indicatorName = context.args.joinToString(" ")
            val indicatorDir = indicatorName.replace(" ", "-")
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "plugin.kts", IdeUtil.kotlinFileType, getNewCommandScript(indicatorName)
            )
            val baseDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(project.baseDir)
            val psiDirectory = DirectoryUtil.createSubdirectories(".spp/plugins/$indicatorDir", baseDirectory, "/")

            PsiNavigateUtil.navigate(psiDirectory.add(psiFile))
        }
    }

    private fun getNewCommandScript(indicatorName: String): String {
        val properIndicatorName = indicatorName.split(" ", "-").map { it.capitalize() }.joinToString("")
        return """
            import com.intellij.openapi.project.Project
            import spp.jetbrains.marker.indicator.LiveIndicator
            import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
            import spp.jetbrains.marker.source.mark.guide.GuideMark
            import spp.plugin.*

            class ${properIndicatorName}Indicator(project: Project) : LiveIndicator(project) {

                override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
                    show("Hello world")
                }
            }

            registerIndicator(${properIndicatorName}Indicator(project))
        """.trimIndent() + "\n"
    }
}

registerCommand(NewIndicatorCommand(project))
