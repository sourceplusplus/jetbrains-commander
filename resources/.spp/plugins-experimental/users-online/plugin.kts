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
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiReference
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import net.miginfocom.swing.MigLayout
import spp.jetbrains.SourceKey
import spp.jetbrains.artifact.service.ArtifactTypeService
import spp.jetbrains.marker.indicator.LiveIndicator
import spp.jetbrains.marker.SourceMarkerUtils
import spp.jetbrains.marker.service.ArtifactNamingService
import spp.jetbrains.marker.service.SourceGuideProvider
import spp.jetbrains.marker.service.define.AbstractSourceGuideProvider
import spp.jetbrains.marker.source.SourceFileMarker
import spp.jetbrains.marker.source.mark.api.SourceMark
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEvent
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.plugin.*
import spp.protocol.artifact.ArtifactType
import spp.protocol.instrument.LiveMeter
import spp.protocol.instrument.location.LiveSourceLocation
import spp.protocol.instrument.meter.MeterType
import spp.protocol.instrument.meter.MetricValue
import spp.protocol.instrument.meter.MetricValueType
import spp.protocol.service.SourceServices.Subscribe.toLiveViewSubscriberAddress
import spp.protocol.view.LiveView
import spp.protocol.view.LiveViewConfig
import spp.protocol.view.LiveViewEvent
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class UsersOnlineIndicator(project: Project) : LiveIndicator(project), AbstractSourceGuideProvider {

    override val listenForEvents = listOf(SourceMarkEventCode.MARK_ADDED)
    private val USER_REFERENCE: SourceKey<Boolean> = SourceKey("USER_REFERENCE")
    private val usersLoggedIn = AtomicReference("n/a")
    private val latestUsernameGauge = AtomicReference("n/a")
    private val usersOnlineGauge = AtomicReference("n/a")
    private lateinit var usersOnlineSubscription: LiveView
    private val demoUserClassname = "spp.example.webapp.demo.UsersOnlineIndicator\$DemoUser"

    //todo: dynamic LiveSourceLocations
    override suspend fun onRegister() {
        SourceGuideProvider.addProvider(this, SourceMarkerUtils.getJvmLanguages())

        //get or add count meter to log in method
        var logInCountMeter = instrumentService.getLiveInstrument("spp_user_login_count").await()
        if (logInCountMeter !is LiveMeter) {
            logInCountMeter = instrumentService.addLiveMeter(
                LiveMeter(
                    MeterType.COUNT,
                    MetricValue(MetricValueType.NUMBER, "1"),
                    location = LiveSourceLocation("spp.example.webapp.demo.UsersOnlineIndicator", 45),
                    id = "spp_user_login_count"
                    //todo: viewConfig = limit to today only
                )
            ).await()
        }

        //get or add last login username gauge to log in method
        var latestLoginGaugeMeter = instrumentService.getLiveInstrument("spp_latest_login_gauge").await()
        if (latestLoginGaugeMeter !is LiveMeter) {
            latestLoginGaugeMeter = instrumentService.addLiveMeter(
                LiveMeter(
                    MeterType.GAUGE,
                    MetricValue(MetricValueType.VALUE_EXPRESSION, "localVariables[user].username"),
                    location = LiveSourceLocation("spp.example.webapp.demo.UsersOnlineIndicator", 46),
                    id = "spp_latest_login_gauge"
                )
            ).await()
        }

        //get or add gauge meter to log out method
        var onlineGaugeMeter = instrumentService.getLiveInstrument("spp_users_online_gauge").await()
        if (onlineGaugeMeter !is LiveMeter) {
            onlineGaugeMeter = instrumentService.addLiveMeter(
                LiveMeter(
                    MeterType.GAUGE,
                    MetricValue(MetricValueType.NUMBER_EXPRESSION, "fields[onlineUsers].size()"),
                    location = LiveSourceLocation("spp.example.webapp.demo.UsersOnlineIndicator", 51),
                    id = "spp_users_online_gauge"
                )
            ).await()
        }

        //subscribe to users online meters
        usersOnlineSubscription = viewService.addLiveView(
            LiveView(
                entityIds = mutableSetOf(
                    logInCountMeter.id!!,
                    latestLoginGaugeMeter.id!!,
                    onlineGaugeMeter.id!!
                ),
                viewConfig = LiveViewConfig(
                    "users-online-indicator",
                    listOf(
                        logInCountMeter.id!!,
                        latestLoginGaugeMeter.id!!,
                        onlineGaugeMeter.id!!
                    )
                )
            )
        ).await()
        log.info("Added users online subscription: $usersOnlineSubscription")

        vertx.eventBus().consumer<JsonObject>(toLiveViewSubscriberAddress(selfInfo.developer.id)).handler {
            val viewEvent = LiveViewEvent(it.body())
            if (usersOnlineSubscription.subscriptionId != viewEvent.subscriptionId) return@handler
            val rawMetrics = JsonArray(viewEvent.metricsData)

            val loggedIn = rawMetrics.getJsonObject(0).getInteger("value")
            usersLoggedIn.set(loggedIn.toString())
            log.debug("Login count: $loggedIn")

            val latestUsername = rawMetrics.getJsonObject(1).getString("value")
            latestUsernameGauge.set(latestUsername)
            log.debug("Latest login: $latestUsername")

            val usersOnline = rawMetrics.getJsonObject(2).getInteger("value")
            usersOnlineGauge.set(usersOnline.toString())
            log.debug("Users online: $usersOnline")
        }
    }

    override fun determineGuideMarks(fileMarker: SourceFileMarker) {
        fileMarker.psiFile.acceptChildren(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                if (element is PsiReference) {
                    //add indicator to DemoUser references
                    val resolved = element.resolve() ?: return
                    val artifactType = ArtifactTypeService.getType(resolved) ?: return
                    if (artifactType != ArtifactType.CLASS) return

                    val name = ArtifactNamingService.getFullyQualifiedName(resolved)
                    if (name.identifier != demoUserClassname) return
                    log.info("Found DemoUser reference: $element")

                    val guideMark = fileMarker.createExpressionSourceMark(element, SourceMark.Type.GUIDE)
                    guideMark.putUserData(USER_REFERENCE, true)
                    guideMark.applyIfMissing()
                } else if (ArtifactTypeService.getType(element) == ArtifactType.CLASS) {
                    //also add indicator to DemoUser class
                    val name = ArtifactNamingService.getFullyQualifiedName(element)
                    if (name.identifier != demoUserClassname) return
                    log.info("Found DemoUser class: $element")

                    val nameIdentifier = (element as PsiNameIdentifierOwner).nameIdentifier!!
                    val guideMark = fileMarker.createExpressionSourceMark(nameIdentifier, SourceMark.Type.GUIDE)
                    guideMark.putUserData(USER_REFERENCE, true)
                    guideMark.applyIfMissing()
                }
            }
        })
    }

    override suspend fun trigger(guideMark: GuideMark, event: SourceMarkEvent) {
        if (guideMark.getUserData(USER_REFERENCE) != true) return

        val indicatorPanel = UsersOnlineIndicatorPanel()
        guideMark.setLiveDisplay(indicatorPanel)

        vertx.setPeriodic(1000) {
            indicatorPanel.online!!.text = usersOnlineGauge.get()
            indicatorPanel.activeToday!!.text = usersLoggedIn.get()
            indicatorPanel.mostRecent!!.text = latestUsernameGauge.get()
        }
    }

    class UsersOnlineIndicatorPanel : JPanel() {
        private var label1: JLabel? = null
        var online: JTextField? = null
        private var label2: JLabel? = null
        var activeToday: JTextField? = null
        private var label3: JLabel? = null
        var mostRecent: JTextField? = null

        init {
            label1 = JLabel()
            online = JTextField()
            label2 = JLabel()
            activeToday = JTextField()
            label3 = JLabel()
            mostRecent = JTextField()

            //======== this ========
            minimumSize = Dimension(225, 125)
            preferredSize = Dimension(225, 125)
            layout = MigLayout(
                "hidemode 3",  // columns
                "[left]" +
                        "[grow,fill]",  // rows
                "[grow]" +
                        "[grow]" +
                        "[grow]"
            )
            background = EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.DOCUMENTATION_COLOR)

            //---- label1 ----
            label1!!.text = "Online:"
            add(label1, "cell 0 0,alignx trailing,growx 0")
            add(online, "cell 1 0")

            //---- label2 ----
            label2!!.text = "Active Today:"
            add(label2, "cell 0 1,alignx trailing,growx 0")
            add(activeToday, "cell 1 1")

            //---- label3 ----
            label3!!.text = "Most Recent:"
            add(label3, "cell 0 2,alignx trailing,growx 0")
            add(mostRecent, "cell 1 2")
        }
    }
}

registerIndicator(UsersOnlineIndicator(project))
