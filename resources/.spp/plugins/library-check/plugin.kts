import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.project.Project
import io.vertx.core.json.JsonArray
import liveplugin.PluginUtil.*
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.jetbrains.command.LiveLocationContext
import spp.jetbrains.marker.impl.ArtifactScopeService
import spp.plugin.*
import spp.protocol.platform.developer.SelfInfo

class LibraryCheckCommand(project: Project) : LiveCommand(project) {
    override val name = "Library Check"
    override val params: List<String> = listOf("Library Name") //todo: optional params
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "Find all jar libraries used in the currently active services" + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val librarySearch = (context.args.firstOrNull() ?: "").lowercase()
        val foundLibraries = mutableSetOf<String>()

        val activeServices = skywalkingMonitorService.getActiveServices()
        if (activeServices.isEmpty()) {
            show("Unable to find active services", notificationType = ERROR)
            return
        }
        val activeServiceInstances = activeServices.flatMap { skywalkingMonitorService.getServiceInstances(it.id) }
        if (activeServiceInstances.isEmpty()) {
            show("Unable to find active service instances", notificationType = ERROR)
            return
        }

        activeServiceInstances.forEach {
            val jarDependencies = it.attributes.find { it.name == "Jar Dependencies" }?.let { JsonArray(it.value) }
            jarDependencies?.let {
                foundLibraries.addAll(
                    jarDependencies.list.map { it.toString() }
                        .filter { it.lowercase().contains(librarySearch) }
                )
            }
        }

        val serviceCount = activeServices.size
        val instanceCount = activeServiceInstances.size
        showInConsole(
            foundLibraries,
            "Libraries Found (Services: $serviceCount - Instances: $instanceCount)",
            project
        )
    }

    override fun isAvailable(selfInfo: SelfInfo, context: LiveLocationContext): Boolean {
        return ArtifactScopeService.isJVM(context.element)
    }
}

registerCommand(LibraryCheckCommand(project))
