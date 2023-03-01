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
import spp.protocol.view.rule.LiveViewRule

class MonitorLifespanCommand(project: Project) : LiveCommand(project) {

    override val name = "monitor-lifespan"
    override fun getDescription(): String = "<html><span style=\"color: $commandTypeColor\">" +
            "My custom live command" + "</span></html>"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val service = skywalkingMonitorService.getCurrentService()
        if (service == null) {
            log.warn("No service selected")
            return
        }

        val className = context.artifactQualifiedName.toClass()!!.identifier
        val id = "${className.replace(".", "-")}-lifespan"
        val countMeter = LiveMeter(
            MeterType.COUNT,
            MetricValue(MetricValueType.NUMBER, "1"),
            location = LiveSourceLocation(
                context.artifactQualifiedName.toClass()!!.identifier + ".<init>()",
                service = service.name
            ),
            id = "$id-count",
            applyImmediately = true,
            meta = mapOf("metric.mode" to "RATE")
        )
        viewService.saveRuleIfAbsent(
            LiveViewRule(
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

        val totalTimeMeter = LiveMeter(
            MeterType.COUNT,
            MetricValue(MetricValueType.OBJECT_LIFESPAN, "0"),
            location = LiveSourceLocation(
                context.artifactQualifiedName.toClass()!!.identifier,
                service = service.name
            ),
            id = "$id-total",
            applyImmediately = true,
            meta = mapOf("metric.mode" to "RATE")
        )
        viewService.saveRuleIfAbsent(
            LiveViewRule(
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
        instrumentService.addLiveInstrument(totalTimeMeter).await()

        val avgMeterId = "$id-avg".replace("-", "_")
        viewService.saveRuleIfAbsent(
            LiveViewRule(
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
