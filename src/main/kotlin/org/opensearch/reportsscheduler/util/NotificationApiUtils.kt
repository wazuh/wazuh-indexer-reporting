/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.reportsscheduler.util

import org.apache.logging.log4j.LogManager
import org.opensearch.OpenSearchSecurityException
import org.opensearch.OpenSearchStatusException
import org.opensearch.commons.notifications.NotificationsPluginInterface
import org.opensearch.commons.notifications.action.GetNotificationConfigRequest
import org.opensearch.commons.notifications.action.GetNotificationConfigResponse
import org.opensearch.commons.notifications.action.SendNotificationResponse
import org.opensearch.commons.notifications.model.ChannelMessage
import org.opensearch.commons.notifications.model.EventSource
import org.opensearch.commons.notifications.model.NotificationConfigInfo
import org.opensearch.commons.notifications.model.SeverityType
import org.opensearch.core.action.ActionListener
import org.opensearch.core.rest.RestStatus
import org.opensearch.reportsscheduler.model.ReportDefinitionDetails
import org.opensearch.transport.client.Client
import org.opensearch.transport.client.node.NodeClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal object NotificationApiUtils {

    private val log = LogManager.getLogger(NotificationApiUtils::class)

    /**
     * Gets a NotificationConfigInfo object by ID if it exists.
     */
    private suspend fun getNotificationConfigInfo(client: NodeClient, id: String): NotificationConfigInfo? {
        return try {
            val res: GetNotificationConfigResponse =
                getNotificationConfig(client, GetNotificationConfigRequest(setOf(id)))
            res.searchResult.objectList.firstOrNull()
        } catch (e: OpenSearchSecurityException) {
            throw e
        } catch (e: OpenSearchStatusException) {
            if (e.status() == RestStatus.NOT_FOUND) {
                log.debug("Notification config [$id] was not found")
            }
            null
        }
    }

    private suspend fun getNotificationConfig(
        client: NodeClient,
        getNotificationConfigRequest: GetNotificationConfigRequest
    ): GetNotificationConfigResponse {
        val getNotificationConfigResponse: GetNotificationConfigResponse =
            NotificationsPluginInterface.suspendUntil {
                this.getNotificationConfig(
                    client,
                    getNotificationConfigRequest,
                    it
                )
            }
        return getNotificationConfigResponse
    }

    /**
     * Creates and sends a notification message to a specific notification channel.
     *
     * This function generates a notification message based on the report definition details,
     * replacing placeholders in the message templates with actual report links and sends
     * the notification through the specified notification channel.
     *
     * @param client The NodeClient used to interact with OpenSearch services
     * @param configInfo The notification configuration containing channel details and settings
     * @param reportDefinitionDetails The report definition containing delivery configuration,
     *                               title, text description, and HTML description
     * @param id The unique identifier of the report instance used to build the report link
     *
     * @throws OpenSearchSecurityException If there are security-related issues during notification sending
     * @throws OpenSearchStatusException If the notification service returns an error status
     */
    private suspend fun createNotification(
        client: NodeClient,
        configInfo: NotificationConfigInfo,
        reportDefinitionDetails: ReportDefinitionDetails,
        id: String
    ) {
        val title: String = reportDefinitionDetails.reportDefinition.delivery!!.title
        val textMessage: String = reportDefinitionDetails.reportDefinition.delivery.textDescription
        val htmlMessage: String? = reportDefinitionDetails.reportDefinition.delivery.htmlDescription

        val reportLink: String =
            buildReportLink(reportDefinitionDetails.reportDefinition.source.origin, reportDefinitionDetails.tenant, id)

        // NOTE {{reportLink}} in the message is replaced by the actual link to the report.
        val body: String = textMessage.replace("{{reportLink}}", reportLink)
        val htmlBody: String? = htmlMessage?.replace("{{reportLink}}", reportLink)

        configInfo.sendNotificationWithHTML(
            client,
            title,
            body,
            htmlBody
        )
    }

    /**
     * Handles the event when a report instance is created by sending notifications to all configured channels.
     *
     * This function is called when a new report instance has been successfully created. It iterates through
     * all notification channels configured in the report definition's delivery settings and sends a
     * notification message to each valid channel. The notification includes the report details and a
     * direct link to access the generated report.
     *
     * @param client The NodeClient used to interact with OpenSearch services and send notifications
     * @param reportDefinitionDetails The complete report definition containing delivery configuration,
     *                               notification channel IDs, message templates, and report metadata
     * @param id The unique identifier of the newly created report instance, used to generate
     *           the report access link and track the notification
     *
     * @throws OpenSearchSecurityException If there are security permissions issues accessing notification configs
     * @throws OpenSearchStatusException If there are errors retrieving notification configurations or sending notifications
     *
     * @see createNotification
     * @see getNotificationConfigInfo
     */
    suspend fun onReportInstanceCreated(
        client: NodeClient,
        reportDefinitionDetails: ReportDefinitionDetails,
        id: String
    ) {
        for (notificationChannelId in reportDefinitionDetails.reportDefinition.delivery!!.configIds) {
            val notificationChannel: NotificationConfigInfo? = getNotificationConfigInfo(
                client,
                notificationChannelId
            )

            if (notificationChannel != null) {
                createNotification(
                    client,
                    notificationChannel,
                    reportDefinitionDetails,
                    id
                )
                log.info("Notification with id $id was sent.")
            } else {
                log.error("NotificationConfigInfo with id $notificationChannelId was not found.")
            }
        }
    }
}

/**
 * Extension function for publishing a notification to a channel in the Notification plugin.
 */
suspend fun NotificationConfigInfo.sendNotificationWithHTML(
    client: Client,
    title: String,
    compiledMessage: String,
    compiledMessageHTML: String?
): String {
    val config = this
    val res: SendNotificationResponse = NotificationsPluginInterface.suspendUntil {
        this.sendNotification(
            (client as NodeClient),
            EventSource(title, config.configId, SeverityType.INFO),
            ChannelMessage(compiledMessage, compiledMessageHTML, null),
            listOf(config.configId),
            it
        )
    }
    validateResponseStatus(res.getStatus(), res.notificationEvent.toString())
    return res.notificationEvent.toString()
}

/**
 * Converts [NotificationsPluginInterface] methods that take a callback into a kotlin suspending function.
 *
 * @param block - a block of code that is passed an [ActionListener] that should be passed to the NotificationsPluginInterface API.
 */
suspend fun <T> NotificationsPluginInterface.suspendUntil(block: NotificationsPluginInterface.(ActionListener<T>) -> Unit): T =
    suspendCoroutine { cont ->
        block(object : ActionListener<T> {
            override fun onResponse(response: T) = cont.resume(response)

            override fun onFailure(e: Exception) = cont.resumeWithException(e)
        })
    }

/**
 * All valid response statuses.
 */
private val VALID_RESPONSE_STATUS = setOf(
    RestStatus.OK.status,
    RestStatus.CREATED.status,
    RestStatus.ACCEPTED.status,
    RestStatus.NON_AUTHORITATIVE_INFORMATION.status,
    RestStatus.NO_CONTENT.status,
    RestStatus.RESET_CONTENT.status,
    RestStatus.PARTIAL_CONTENT.status,
    RestStatus.MULTI_STATUS.status
)

@Throws(OpenSearchStatusException::class)
fun validateResponseStatus(restStatus: RestStatus, responseContent: String) {
    if (!VALID_RESPONSE_STATUS.contains(restStatus.status)) {
        throw OpenSearchStatusException("Failed: $responseContent", restStatus)
    }
}
