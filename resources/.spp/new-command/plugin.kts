import com.intellij.ide.util.DirectoryUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PsiNavigateUtil
import liveplugin.getCommandTypeColor
import liveplugin.implementation.common.IdeUtil
import liveplugin.message
import liveplugin.registerCommand
import liveplugin.show
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext

class NewCommandCommand(project: Project) : LiveCommand(project) {
    override val name = message("New Command")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            "Add new custom command" + "</span></html>"
    override val selectedIcon = "new-command/icons/new-command_selected.svg"
    override val unselectedIcon = "new-command/icons/new-command_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            if (context.args.isEmpty()) {
                show("Please enter command name")
                return@runWriteAction
            }

            val commandName = context.args[0]
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    "plugin.kts", IdeUtil.kotlinFileType, getNewCommandScript(commandName)
            )
            val baseDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(project.baseDir)
            val psiDirectory = DirectoryUtil.createSubdirectories(".spp/$commandName", baseDirectory, "/")

            PsiNavigateUtil.navigate(psiDirectory.add(psiFile))
        }
    }

    private fun getNewCommandScript(commandName: String): String {
        val properCommandName = commandName.split(" ", "-").map { it.capitalize() }.joinToString("")
        return """
            import com.intellij.openapi.project.Project
            import liveplugin.*
            import spp.jetbrains.marker.extend.*

            class ${properCommandName}Command(project: Project) : LiveCommand(project) {
                override val name = "$commandName"
                override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
                        "My custom command" + "</span></html>"

                override fun trigger(context: LiveCommandContext) {
                    show("Hello world")
                }
            }

            registerCommand { ${properCommandName}Command(project!!) }
        """.trimIndent()
    }
}

registerCommand { NewCommandCommand(project!!) }
