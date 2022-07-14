/*
 * Source++, the open-source live coding platform.
 * Copyright (C) 2022 CodeBrig, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package liveplugin.implementation.indicator.impl

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.firstOrNull
import kotlinx.coroutines.runBlocking
import liveplugin.implementation.indicator.LiveIndicatorService
import spp.indicator.LiveIndicator
import spp.jetbrains.marker.SourceMarker
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventListener
import spp.jetbrains.marker.source.mark.guide.GuideMark

class LiveIndicatorServiceImpl(val project: Project) : LiveIndicatorService {

    private val indicators = mutableMapOf<LiveIndicator, SourceMarkEventListener>()

    override fun registerLiveIndicator(indicator: LiveIndicator) {
        val eventListener = SourceMarkEventListener {
            if (indicator.listenForEvents.contains(it.eventCode) && it.sourceMark is GuideMark) {
                runBlocking {
                    indicator.trigger(it.sourceMark as GuideMark, it)
                }
            }
        }
        SourceMarker.addGlobalSourceMarkEventListener(eventListener)
        indicators[indicator] = eventListener

        runBlocking {
            indicator.onRegister()
        }
    }

    override fun unregisterLiveIndicator(indicator: LiveIndicator) {
        indicators.filter { it.key == indicator }.firstOrNull()?.let {
            SourceMarker.removeGlobalSourceMarkEventListener(it.value)
            indicators.remove(it.key)

            runBlocking {
                indicator.onUnregister()
            }
        }
    }

    override fun getRegisteredLiveIndicators(): List<LiveIndicator> {
        return indicators.keys.toList()
    }
}
