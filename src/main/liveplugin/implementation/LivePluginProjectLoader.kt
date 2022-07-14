package liveplugin.implementation

import com.intellij.ide.impl.isTrusted
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import liveplugin.implementation.actions.RunPluginAction
import liveplugin.implementation.actions.UnloadPluginAction
import liveplugin.implementation.command.LiveCommandService
import liveplugin.implementation.command.impl.LiveCommandServiceImpl
import liveplugin.implementation.common.MapDataContext
import liveplugin.implementation.common.livePluginNotificationGroup
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.indicator.LiveIndicatorService
import liveplugin.implementation.indicator.impl.LiveIndicatorServiceImpl
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Needed for manually loading LivePlugin when Source++ Plugin is installed after project is opened.
 */
object LivePluginProjectLoader {

    fun projectOpened(project: Project) {
        @Suppress("UnstableApiUsage")
        if (!project.isTrusted()) {
            val message = "Skipped execution of project specific plugins because the project is not trusted."
            livePluginNotificationGroup.createNotification("Live plugin", message, INFORMATION).notify(project)
            return
        }
        if (project.getUserData(LiveIndicatorService.KEY) != null) return
        if (project.getUserData(LiveCommandService.KEY) != null) return
        val sppResourcesLocation = extractSppResources()

        project.putUserData(LiveIndicatorService.KEY, LiveIndicatorServiceImpl(project))
        project.putUserData(LiveIndicatorService.LIVE_INDICATOR_LOADER) {
            val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT.name to project))
            val dummyEvent = AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)
            val sppIndicatorsLocation = File(sppResourcesLocation, "indicators")
            RunPluginAction.runIndicators(sppIndicatorsLocation.toFilePath().listFiles().toLivePlugins(), dummyEvent)
            project.putUserData(LiveIndicatorService.SPP_INDICATORS_LOCATION, sppIndicatorsLocation)

            val projectPath = project.basePath?.toFilePath() ?: return@putUserData
            val liveIndicatorsPath = projectPath + LivePluginPaths.liveIndicatorsProjectDirName
            RunPluginAction.runIndicators(liveIndicatorsPath.listFiles().toLivePlugins(), dummyEvent)
        }
        project.putUserData(LiveCommandService.KEY, LiveCommandServiceImpl(project))
        project.putUserData(LiveCommandService.LIVE_COMMAND_LOADER) {
            val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT.name to project))
            val dummyEvent = AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)
            val sppCommandsLocation = File(sppResourcesLocation, "commands")
            RunPluginAction.runCommands(sppCommandsLocation.toFilePath().listFiles().toLivePlugins(), dummyEvent)
            project.putUserData(LiveCommandService.SPP_COMMANDS_LOCATION, sppCommandsLocation)

            val projectPath = project.basePath?.toFilePath() ?: return@putUserData
            val liveCommandsPath = projectPath + LivePluginPaths.liveCommandsProjectDirName
            RunPluginAction.runCommands(liveCommandsPath.listFiles().toLivePlugins(), dummyEvent)
        }
    }

    fun projectClosing(project: Project) {
        project.getUserData(LiveIndicatorService.SPP_INDICATORS_LOCATION)?.let {
            UnloadPluginAction.unloadPlugins(it.toFilePath().listFiles().toLivePlugins())
            it.deleteRecursively()
        }
        project.putUserData(LiveIndicatorService.SPP_INDICATORS_LOCATION, null)
        project.getUserData(LiveCommandService.SPP_COMMANDS_LOCATION)?.let {
            UnloadPluginAction.unloadPlugins(it.toFilePath().listFiles().toLivePlugins())
            it.deleteRecursively()
        }
        project.putUserData(LiveCommandService.SPP_COMMANDS_LOCATION, null)
        project.putUserData(LiveCommandService.LIVE_COMMAND_LOADER, null)
        project.putUserData(LiveCommandService.KEY, null)
        project.putUserData(LiveIndicatorService.LIVE_INDICATOR_LOADER, null)
        project.putUserData(LiveIndicatorService.KEY, null)

        val projectPath = project.basePath?.toFilePath() ?: return
        val liveCommandsPath = projectPath + LivePluginPaths.liveCommandsProjectDirName
        val liveIndicatorsPath = projectPath + LivePluginPaths.liveIndicatorsProjectDirName

        UnloadPluginAction.unloadPlugins(
            liveCommandsPath.listFiles().toLivePlugins() + liveIndicatorsPath.listFiles().toLivePlugins()
        )
    }

    private fun extractSppResources(): File {
        val tmpDir = Files.createTempDirectory("spp-resources").toFile()
        tmpDir.deleteOnExit()
        val destDir = tmpDir.absolutePath

        val jar = JarFile(File(PathManager.getJarPathForClass(LivePluginProjectLoader::class.java)))
        val enumEntries: Enumeration<*> = jar.entries()
        while (enumEntries.hasMoreElements()) {
            val file = enumEntries.nextElement() as JarEntry
            if (!file.name.startsWith(".spp")) continue

            val f = File(destDir + File.separator + file.name)
            if (file.isDirectory) {
                f.mkdir()
                continue
            }

            val inputStream = jar.getInputStream(file)
            val fos = FileOutputStream(f)
            while (inputStream.available() > 0) {
                fos.write(inputStream.read())
            }
            fos.close()
            inputStream.close()
        }
        jar.close()

        return File(destDir, ".spp")
    }
}
