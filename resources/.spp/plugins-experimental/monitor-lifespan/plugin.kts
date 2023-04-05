import com.intellij.openapi.project.Project
import io.vertx.kotlin.coroutines.await
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.command.LiveCommand
import spp.jetbrains.command.LiveCommandContext
import spp.plugin.registerCommand
import spp.protocol.instrument.LiveMeter
import spp.protocol.instrument.location.LiveSourceLocation
import spp.protocol.instrument.meter.MeterType
import spp.protocol.instrument.meter.MetricValue
import spp.protocol.instrument.meter.MetricValueType
import spp.protocol.view.rule.ViewRule

class MonitorLifespanCommand(project: Project) : LiveCommand(project) {

    override val name = "monitor-lifespan"
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "My custom live command" + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val service = statusService.getCurrentService()
        if (service == null) {
            log.warn("No service selected")
            return
        }

        //determine the object we're going to monitor
        val className = context.artifactQualifiedName.toClass()?.identifier
        if (className == null) {
            log.warn("Could not determine class name")
            return
        }

        //create a live meter & rule that increments every time the object is created
        val id = "${className.replace(".", "-")}-lifespan"
        val countMeter = LiveMeter(
            MeterType.COUNT,
            MetricValue(MetricValueType.NUMBER, "1"),
            location = LiveSourceLocation(
                context.artifactQualifiedName.toClass()!!.identifier + ".<init>(...)",
                service = service.name
            ),
            id = "$id-count",
            applyImmediately = true,
            meta = mapOf("metric.mode" to "RATE")
        )
        viewService.saveRuleIfAbsent(
            ViewRule(
                name = countMeter.toMetricIdWithoutPrefix(),
                exp = buildString {
                    append("(")
                    append(countMeter.toMetricIdWithoutPrefix())
                    append(".sum(['service', 'instance'])")
                    append(".downsampling(SUM)")
                    append(")")
                    append(".instance(['service'], ['instance'], Layer.GENERAL)")
                }
            )
        ).await()
        instrumentService!!.addLiveInstrument(countMeter).await()

        //create a live meter & rule that tracks the total time the object is alive
        val totalTimeMeter = LiveMeter(
            MeterType.COUNT,
            MetricValue(MetricValueType.OBJECT_LIFESPAN, "0"),
            location = LiveSourceLocation(
                context.artifactQualifiedName.toClass()!!.identifier + ".<init>(...)",
                service = service.name
            ),
            id = "$id-total",
            applyImmediately = true,
            meta = mapOf("metric.mode" to "RATE")
        )
        viewService.saveRuleIfAbsent(
            ViewRule(
                name = totalTimeMeter.toMetricIdWithoutPrefix(),
                exp = buildString {
                    append("(")
                    append(totalTimeMeter.toMetricIdWithoutPrefix())
                    append(".sum(['service', 'instance'])")
                    append(".downsampling(SUM)")
                    append(")")
                    append(".instance(['service'], ['instance'], Layer.GENERAL)")
                }
            )
        ).await()
        instrumentService!!.addLiveInstrument(totalTimeMeter).await()

        //create a rule that calculates the average lifespan (total time / count)
        val avgMeterId = "$id-avg".replace("-", "_")
        viewService.saveRuleIfAbsent(
            ViewRule(
                name = avgMeterId,
                exp = buildString {
                    append("(")
                    append(totalTimeMeter.toMetricIdWithoutPrefix())
                    append("/")
                    append(countMeter.toMetricIdWithoutPrefix())
                    append(").downsampling(LATEST)")
                    append(".instance(['service'], ['instance'], Layer.GENERAL)")
                }
            )
        ).await()

//        val serviceInstance = managementService.getInstances(service.id).await().first()
//        LiveViewChartManager.getInstance(project).showChart(
//            serviceInstance.id,
//            className.substringAfterLast(".") + " Lifespan",
//            "ServiceInstance",
//            listOf(MetricType("spp_$avgMeterId"))
//        )
    }
}

registerCommand(MonitorLifespanCommand(project))
