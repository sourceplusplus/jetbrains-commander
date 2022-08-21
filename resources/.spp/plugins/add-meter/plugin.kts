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

class AddMeterCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_meter")
    override val description = "<html><span style=\"color: $commandTypeColor\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: $commandHighlightColor\">" + message("on_line") +
            " *lineNumber*</span></html>"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            liveStatusManager.showMeterStatusBar(project.currentEditor!!, context.lineNumber)
        }
    }

    override fun isAvailable(sourceMark: SourceMark): Boolean {
        return liveInstrumentService != null
                && ArtifactNameUtils.hasFunctionSignature(sourceMark.artifactQualifiedName)
    }
}

registerCommand(AddMeterCommand(project))
