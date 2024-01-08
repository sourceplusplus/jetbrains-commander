/*
 * Source++, the continuous feedback platform for developers.
 * Copyright (C) 2022-2024 CodeBrig, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.intellij.openapi.project.Project
import io.vertx.kotlin.coroutines.await
import spp.jetbrains.PluginUI.commandTypeColor
import spp.jetbrains.marker.command.LiveCommand
import spp.jetbrains.marker.command.LiveCommandContext
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
            id = "${id}_count",
            applyImmediately = true,
            meta = mapOf("metric.mode" to "RATE")
        )
        viewService.saveRuleIfAbsent(
            ViewRule(
                name = countMeter.id!!,
                exp = buildString {
                    append("(")
                    append(countMeter.id!!)
                    append(".sum(['service', 'instance'])")
                    append(".downsampling(SUM)")
                    append(")")
                    append(".instance(['service'], ['instance'], Layer.GENERAL)")
                },
                meterIds = listOf(countMeter.id!!)
            )
        ).await()
        instrumentService.addLiveInstrument(countMeter).await()

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
                name = totalTimeMeter.id!!,
                exp = buildString {
                    append("(")
                    append(totalTimeMeter.id)
                    append(".sum(['service', 'instance'])")
                    append(".downsampling(SUM)")
                    append(")")
                    append(".instance(['service'], ['instance'], Layer.GENERAL)")
                },
                meterIds = listOf(totalTimeMeter.id!!)
            )
        ).await()
        instrumentService.addLiveInstrument(totalTimeMeter).await()

        //create a rule that calculates the average lifespan (total time / count)
        val avgMeterId = "$id-avg".replace("-", "_")
        viewService.saveRuleIfAbsent(
            ViewRule(
                name = avgMeterId,
                exp = buildString {
                    append("(")
                    append(totalTimeMeter.id)
                    append("/")
                    append(countMeter.id)
                    append(").downsampling(LATEST)")
                    append(".instance(['service'], ['instance'], Layer.GENERAL)")
                },
                meterIds = listOf(totalTimeMeter.id!!, countMeter.id!!)
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
