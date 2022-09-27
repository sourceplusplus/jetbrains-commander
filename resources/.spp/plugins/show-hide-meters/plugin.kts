import com.intellij.openapi.project.Project
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.marker.SourceMarker
import spp.jetbrains.sourcemarker.mark.SourceMarkKeys
import spp.plugin.*
import spp.protocol.instrument.LiveInstrumentType
import javax.swing.Icon

class ShowHideMetersCommand(
    project: Project,
    override val name: String = "show-hide-meters"
) : LiveCommand(project) {

    private var currentVisibility = false //hidden by default

    override var selectedIcon: Icon? = null
        get() {
            return if (currentVisibility) {
                findIcon("icons/hide-selected.svg")
            } else {
                findIcon("icons/show-selected.svg")
            }
        }

    override var unselectedIcon: Icon? = null
        get() {
            return if (currentVisibility) {
                findIcon("icons/hide-unselected.svg")
            } else {
                findIcon("icons/show-unselected.svg")
            }
        }

    override fun getTriggerName(): String {
        return if (currentVisibility) "Hide Meters" else "Show Meters"
    }

    override fun getDescription(): String {
        return if (currentVisibility) {
            buildString {
                append("<html><span style=\"color: ").append(commandTypeColor).append("\">")
                append("Hide live meter gutter icons")
                append("</span></html>")
            }
        } else {
            buildString {
                append("<html><span style=\"color: ").append(commandTypeColor).append("\">")
                append("Show live meter gutter icons")
                append("</span></html>")
            }
        }
    }

    override fun trigger(context: LiveCommandContext) {
        currentVisibility = !currentVisibility

        SourceMarker.getInstance(project).getGutterMarks().filter {
            it.getUserData(SourceMarkKeys.INSTRUMENT_TYPE) == LiveInstrumentType.METER
        }.forEach { it.setVisible(currentVisibility) }
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        return SourceMarker.getInstance(project).getGutterMarks().any {
            it.getUserData(SourceMarkKeys.INSTRUMENT_TYPE) == LiveInstrumentType.METER
        }
    }
}

registerCommand(ShowHideMetersCommand(project))
