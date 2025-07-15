/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.reportsscheduler.action

import kotlinx.coroutines.runBlocking
import org.opensearch.action.ActionType
import org.opensearch.action.support.ActionFilters
import org.opensearch.common.inject.Inject
import org.opensearch.commons.authuser.User
import org.opensearch.commons.notifications.model.NotificationConfigInfo
import org.opensearch.core.xcontent.NamedXContentRegistry
import org.opensearch.reportsscheduler.model.OnDemandReportCreateRequest
import org.opensearch.reportsscheduler.model.OnDemandReportCreateResponse
import org.opensearch.reportsscheduler.model.ReportDefinitionDetails
import org.opensearch.reportsscheduler.util.NotificationApiUtils.getNotificationConfigInfo
import org.opensearch.reportsscheduler.util.buildReportLink
import org.opensearch.reportsscheduler.util.logger
import org.opensearch.reportsscheduler.util.sendNotificationWithHTML
import org.opensearch.transport.TransportService
import org.opensearch.transport.client.Client
import org.opensearch.transport.client.node.NodeClient
import kotlin.getValue

/**
 * On-Demand ReportCreate transport action
 */
internal class OnDemandReportCreateAction @Inject constructor(
    transportService: TransportService,
    client: Client,
    actionFilters: ActionFilters,
    val xContentRegistry: NamedXContentRegistry
) : PluginBaseAction<OnDemandReportCreateRequest, OnDemandReportCreateResponse>(
    NAME,
    transportService,
    client,
    actionFilters,
    ::OnDemandReportCreateRequest
) {
    companion object {
        private const val NAME = "cluster:admin/opendistro/reports/definition/on_demand"
        internal val ACTION_TYPE = ActionType(NAME, ::OnDemandReportCreateResponse)
    }

    /**
     * {@inheritDoc}
     */
    override fun executeRequest(request: OnDemandReportCreateRequest, user: User?): OnDemandReportCreateResponse {
        val response = ReportInstanceActions.createOnDemandFromDefinition(request, user)
        runBlocking {
            val reportDefinitionId = response.reportInstance.reportDefinitionDetails!!.id
            val configInfo: NotificationConfigInfo? = getNotificationConfigInfo(
                client as NodeClient,
                reportDefinitionId
            )
            createNotification(configInfo, response.reportInstance.reportDefinitionDetails, response.reportInstance.id, client)
        }
        return response
    }
}

private suspend fun createNotification(
    configInfo: NotificationConfigInfo?,
    reportDefinitionDetails: ReportDefinitionDetails?,
    id: String,
    client: Client
) {
    val log by logger(OnDemandReportCreateAction::class.java)
    val title: String = reportDefinitionDetails?.reportDefinition?.delivery?.title ?: "title"
    val textMessage: String = reportDefinitionDetails?.reportDefinition?.delivery?.textDescription ?: "message"
    val htmlMessage: String? = reportDefinitionDetails?.reportDefinition?.delivery?.htmlDescription ?: "html message"

    val urlDefinition: String =
        buildReportLink(reportDefinitionDetails?.reportDefinition?.source?.origin ?: "origin", reportDefinitionDetails?.tenant ?: "tenant", id)

    val textWithURL: String =
        textMessage.replace("{{urlDefinition}}", urlDefinition)
    val htmlWithURL: String? =
        htmlMessage?.replace("{{urlDefinition}}", urlDefinition)

    log.debug("HTML message: $htmlMessage") // TODO remove
    configInfo?.sendNotificationWithHTML(
        client,
        title,
        textWithURL,
        htmlWithURL
    )
}
