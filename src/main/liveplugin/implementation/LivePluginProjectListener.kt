package liveplugin.implementation

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class LivePluginProjectListener : ProjectManagerListener {

    override fun projectOpened(project: Project) = LivePluginProjectLoader.projectOpened(project)
    override fun projectClosing(project: Project) = LivePluginProjectLoader.projectClosing(project)
}
