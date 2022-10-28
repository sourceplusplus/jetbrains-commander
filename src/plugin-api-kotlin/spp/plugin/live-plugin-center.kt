@file:Suppress("unused", "UnusedReceiverParameter")

package spp.plugin

import com.intellij.openapi.util.IconLoader
import io.vertx.core.Vertx
import liveplugin.implementation.pluginrunner.kotlin.LivePluginScript
import spp.jetbrains.UserData
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.indicator.LiveIndicator
import spp.jetbrains.marker.SourceMarker
import spp.jetbrains.marker.source.info.EndpointDetector
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.jetbrains.plugin.LivePluginService
import java.io.File
import javax.swing.Icon

fun LivePluginScript.registerCommand(liveCommand: LiveCommand) {
    //set default icons (if not set)
    if (liveCommand.selectedIcon == null) {
        liveCommand.selectedIcon = findIcon("icons/selected.svg")
    }
    if (liveCommand.unselectedIcon == null) {
        liveCommand.unselectedIcon = findIcon("icons/unselected.svg")
    }

    LivePluginService.getInstance(project).registerLiveCommand(liveCommand)
    pluginDisposable.whenDisposed {
        LivePluginService.getInstance(project).unregisterLiveCommand(liveCommand.name)
    }
}

fun LivePluginScript.registerIndicator(liveIndicator: LiveIndicator) {
    LivePluginService.getInstance(project).registerLiveIndicator(liveIndicator)
    pluginDisposable.whenDisposed {
        LivePluginService.getInstance(project).unregisterLiveIndicator(liveIndicator)
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

fun LivePluginScript.findByEndpointName(endpointName: String): GuideMark? {
    return SourceMarker.getInstance(project).getSourceMarks().filterIsInstance<GuideMark>().firstOrNull {
        it.getUserData(EndpointDetector.DETECTED_ENDPOINTS)?.any { it.name == endpointName } == true
    }
}

val LivePluginScript.vertx: Vertx
    get() = UserData.vertx(project)
