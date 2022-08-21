import com.intellij.ide.util.DirectoryUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PsiNavigateUtil
import liveplugin.implementation.common.IdeUtil
import spp.command.*
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.plugin.*

class NewIndicatorCommand(project: Project) : LiveCommand(project) {
    override val name = message("New Indicator")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            "Add new custom live indicator" + "</span></html>"
    override val params: List<String> = listOf("Indicator Name")

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
            import spp.indicator.*
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
