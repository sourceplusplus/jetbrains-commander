import com.intellij.ide.util.DirectoryUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PsiNavigateUtil
import liveplugin.implementation.common.IdeUtil
import spp.plugin.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message

class NewIndicatorCommand : LiveCommand() {
    override val name = message("New Indicator")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            "Add new custom live indicator" + "</span></html>"
    override val params: List<String> = listOf("Indicator Name")
    override var selectedIcon = findIcon("icons/new-indicator_selected.svg")
    override var unselectedIcon = findIcon("icons/new-indicator_unselected.svg")

    override fun trigger(context: LiveCommandContext) {
        if (context.args.isEmpty()) {
            show("Missing indicator name", notificationType = NotificationType.ERROR)
            return
        }

        runWriteAction {
            val indicatorName = context.args.joinToString(" ")
            val indicatorDir = indicatorName.replace(" ", "-")
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    "indicator.kts", IdeUtil.kotlinFileType, getNewCommandScript(indicatorName)
            )
            val baseDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(project.baseDir)
            val psiDirectory = DirectoryUtil.createSubdirectories(".spp/indicators/$indicatorDir", baseDirectory, "/")

            PsiNavigateUtil.navigate(psiDirectory.add(psiFile))
        }
    }

    private fun getNewCommandScript(indicatorName: String): String {
        val properIndicatorName = indicatorName.split(" ", "-").map { it.capitalize() }.joinToString("")
        return """
            import spp.indicator.*
            import spp.plugin.*
            import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
            import spp.jetbrains.marker.source.mark.guide.GuideMark

            class ${properIndicatorName}Indicator : LiveIndicator() {

                override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
                    show("Hello world")
                }
            }

            registerIndicator(${properIndicatorName}Indicator())
        """.trimIndent() + "\n"
    }
}

registerCommand(NewIndicatorCommand())
