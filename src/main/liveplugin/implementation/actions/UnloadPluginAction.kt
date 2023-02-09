package liveplugin.implementation.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.actions.RunPluginAction.Companion.pluginNameInActionText
import liveplugin.implementation.common.Icons.unloadPluginIcon
import liveplugin.implementation.livePlugins
import liveplugin.implementation.pluginrunner.Binding
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.unloadPlugins

class UnloadPluginAction: AnAction("Unload Plugin", "Unload live plugin", unloadPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        unloadPlugins(event.project, event.livePlugins())
    }

    override fun update(event: AnActionEvent) {
        val livePlugins = event.livePlugins().filter { it.canBeUnloaded(event.project) }
        event.presentation.isEnabled = livePlugins.isNotEmpty()
        if (event.presentation.isEnabled) {
            event.presentation.setText("Unload ${pluginNameInActionText(livePlugins)}", false)
        }
    }

    companion object {
        @JvmStatic fun unloadPlugins(project: Project?, livePlugins: Collection<LivePlugin>) {
            livePlugins.forEach { Binding.lookup(it)?.dispose() }
        }
    }
}

fun LivePlugin.canBeUnloaded(project: Project?) = Binding.lookup(this) != null
