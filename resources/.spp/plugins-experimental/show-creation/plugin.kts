import com.intellij.openapi.project.Project
import io.vertx.kotlin.coroutines.await
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
import spp.jetbrains.view.manager.LiveViewChartManager
import spp.plugin.registerCommand
import spp.protocol.artifact.metrics.MetricType

class ShowCreationCommand(project: Project) : LiveCommand(project) {

    override val name = "show-creation"
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "My custom live command" + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val service = statusService.getCurrentService()
        if (service == null) {
            log.warn("No service selected")
            return
        }

        val className = context.artifactQualifiedName.toClass()!!.identifier
        val id = "${className.replace(".", "-")}-lifespan"
        val avgMeterId = "spp-$id-avg".replace("-", "_")

        val serviceInstance = managementService.getInstances(service).await().first()
        LiveViewChartManager.getInstance(project).showChart(
            serviceInstance.id,
            "Live '${className.substringAfterLast(".")}' Objects",
            "ServiceInstance",
            listOf(MetricType(avgMeterId))
        )
    }
}

registerCommand(ShowCreationCommand(project))
