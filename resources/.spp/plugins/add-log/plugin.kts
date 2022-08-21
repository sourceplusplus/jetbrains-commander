import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginBundle.message
import spp.jetbrains.PluginUI.commandHighlightColor
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.marker.source.mark.api.SourceMark
import spp.plugin.*
import spp.protocol.artifact.ArtifactNameUtils

class AddLogCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_log")
    override val description = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("on_line") +
            " *lineNumber*</span></html>"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            liveStatusManager.showLogStatusBar(project.currentEditor!!, context.lineNumber, false)
        }
    }

    override fun isAvailable(sourceMark: SourceMark): Boolean {
        return liveInstrumentService != null
                && ArtifactNameUtils.hasFunctionSignature(sourceMark.artifactQualifiedName)
    }
}

registerCommand(AddLogCommand(project))
