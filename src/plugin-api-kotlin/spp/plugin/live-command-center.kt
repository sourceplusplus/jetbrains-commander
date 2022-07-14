@file:Suppress("unused")

package spp.plugin

import com.intellij.openapi.util.IconLoader
import liveplugin.implementation.command.LiveCommandService
import liveplugin.implementation.command.LiveCommandService.Companion.SPP_COMMANDS_LOCATION
import liveplugin.implementation.indicator.LiveIndicatorService
import liveplugin.implementation.indicator.LiveIndicatorService.Companion.SPP_INDICATORS_LOCATION
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
        LiveIndicatorService.getInstance(project).unregisterLiveIndicator(liveIndicator.name)
    }
}

fun LivePluginScript.findIcon(path: String): Icon? {
    val commandBasePath = project.basePath?.let { File(it, ".spp${File.separatorChar}commands").absolutePath } ?: ""
    val indicatorBasePath = project.basePath?.let { File(it, ".spp${File.separatorChar}indicators").absolutePath } ?: ""
    val internalCommandBasePath = SPP_COMMANDS_LOCATION.let { project.getUserData(it).toString() }
    val internalIndicatorBasePath = SPP_INDICATORS_LOCATION.let { project.getUserData(it).toString() }

    val iconPath = if (File(internalCommandBasePath, path).exists()) {
        internalCommandBasePath + File.separator + path
    } else if (File(internalIndicatorBasePath, path).exists()) {
        internalIndicatorBasePath + File.separator + path
    } else if (File(commandBasePath, path).exists()) {
        commandBasePath + File.separator + path
    } else if (File(indicatorBasePath, path).exists()) {
        indicatorBasePath + File.separator + path
    } else {
        path
    }
    return IconLoader.findIcon(File(iconPath).toURL())
}
