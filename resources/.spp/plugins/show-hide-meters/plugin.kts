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
import com.intellij.openapi.project.Project
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.marker.command.LiveLocationContext
import spp.jetbrains.marker.SourceMarker
import spp.jetbrains.marker.SourceMarkerKeys
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
            it.getUserData(SourceMarkerKeys.INSTRUMENT_TYPE) == LiveInstrumentType.METER
        }.forEach { it.setVisible(currentVisibility) }
    }

    override fun isAvailable(context: LiveLocationContext): Boolean {
        return SourceMarker.getInstance(project).getGutterMarks().any {
            it.getUserData(SourceMarkerKeys.INSTRUMENT_TYPE) == LiveInstrumentType.METER
        }
    }
}

registerCommand(ShowHideMetersCommand(project))
