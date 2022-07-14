@file:Suppress("unused")

package spp.plugin

import com.intellij.openapi.util.IconLoader
import liveplugin.implementation.command.LiveCommandService
import liveplugin.implementation.indicator.LiveIndicatorService
import liveplugin.implementation.pluginrunner.kotlin.LivePluginScript
import spp.command.LiveCommand
import spp.indicator.LiveIndicator
import java.io.File
import javax.swing.Icon

fun LivePluginScript.registerCommand(liveCommand: LiveCommand) {
    LiveCommandService.getInstance(project).registerLiveCommand(liveCommand)

    pluginDisposable.whenDisposed {
        LiveCommandService.getInstance(project).unregisterLiveCommand(liveCommand.name)
    }
}

fun LivePluginScript.registerIndicator(liveIndicator: LiveIndicator) {
    LiveIndicatorService.getInstance(project).registerLiveIndicator(liveIndicator)

    pluginDisposable.whenDisposed {
        LiveIndicatorService.getInstance(project).unregisterLiveIndicator(liveIndicator)
    }
}

fun LivePluginScript.findIcon(path: String): Icon? {
    val iconPath = if (File(pluginPath, path).exists()) {
        pluginPath + File.separator + path
    } else {
        path
    }
    return IconLoader.findIcon(File(iconPath).toURL())
}
