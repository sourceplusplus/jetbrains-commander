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
package liveplugin.implementation.indicator

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import spp.indicator.LiveIndicator
import java.io.File

interface LiveIndicatorService {
    fun registerLiveIndicator(indicator: LiveIndicator)
    fun unregisterLiveIndicator(indicatorName: String)
    fun getRegisteredLiveIndicators(): List<LiveIndicator>

    companion object {
        val KEY = Key.create<LiveIndicatorService>("SPP_LIVE_INDICATOR_SERVICE")
        val LIVE_INDICATOR_LOADER = Key.create<() -> Unit>("SPP_LIVE_INDICATOR_LOADER")
        val SPP_INDICATORS_LOCATION = Key.create<File>("SPP_INDICATORS_LOCATION")

        fun getInstance(project: Project): LiveIndicatorService {
            return project.getUserData(KEY)!!
        }
    }
}
