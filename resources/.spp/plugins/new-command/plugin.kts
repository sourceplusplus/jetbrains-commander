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
import com.intellij.ide.util.DirectoryUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PsiNavigateUtil
import liveplugin.implementation.common.IdeUtil
import spp.jetbrains.*
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.*
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.plugin.*

class NewCommandCommand(project: Project) : LiveCommand(project) {
    override val name = message("New Command")
    override val params: List<String> = listOf("Command Name")
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "Add new custom live command" + "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        if (context.args.isEmpty()) {
            show("Missing command name", notificationType = NotificationType.ERROR)
            return
        }

        runWriteAction {
            val commandName = context.args.joinToString(" ")
            val commandDir = commandName.replace(" ", "-")
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "plugin.kts", IdeUtil.kotlinFileType, getNewCommandScript(commandName)
            )
            val baseDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(project.baseDir)
            val psiDirectory = DirectoryUtil.createSubdirectories(".spp/plugins/$commandDir", baseDirectory, "/")

            PsiNavigateUtil.navigate(psiDirectory.add(psiFile))
        }
    }

    private fun getNewCommandScript(commandName: String): String {
        val properCommandName = commandName.split(" ", "-").map { it.capitalize() }.joinToString("")
        return """
            import com.intellij.openapi.project.Project
            import spp.jetbrains.PluginUI.commandTypeColor
            import spp.jetbrains.command.LiveCommand
            import spp.jetbrains.command.LiveCommandContext
            import spp.plugin.*

            class ${properCommandName}Command(project: Project) : LiveCommand(project) {
                override val name = "$commandName"
                override fun getDescription(): String = "<html><span style=\"color: ${'$'}commandTypeColor\">" +
                        "My custom live command" + "</span></html>"

                override fun trigger(context: LiveCommandContext) {
                    show("Hello world")
                }
            }

            registerCommand(${properCommandName}Command(project))
        """.trimIndent() + "\n"
    }
}

registerCommand(NewCommandCommand(project))
