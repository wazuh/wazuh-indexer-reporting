/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.reportsscheduler.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opensearch.OpenSearchSecurityException
import org.opensearch.OpenSearchStatusException
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.client.Client
import org.opensearch.client.node.NodeClient
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
import org.opensearch.index.query.QueryBuilders
import org.opensearch.jobscheduler.spi.JobExecutionContext
import org.opensearch.jobscheduler.spi.ScheduledJobParameter
import org.opensearch.jobscheduler.spi.ScheduledJobRunner
import org.opensearch.reportsscheduler.ReportsSchedulerPlugin.Companion.LOG_PREFIX
import org.opensearch.reportsscheduler.index.ReportInstancesIndex
import org.opensearch.reportsscheduler.model.ReportDefinitionDetails
import org.opensearch.reportsscheduler.model.ReportInstance
import org.opensearch.reportsscheduler.util.buildReportLink
import org.opensearch.reportsscheduler.util.logger
import org.opensearch.search.builder.SearchSourceBuilder
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal object ReportDefinitionJobRunner : ScheduledJobRunner {
    private val log by logger(ReportDefinitionJobRunner::class.java)
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    lateinit var nodeClient: NodeClient
    const val MAX_SIZE: Int = 10

    /**
     * Wazuh - Gets a NotificationConfigInfo object by ID if it exists.
     */
    private suspend fun getNotificationConfigInfo(client: NodeClient, id: String): NotificationConfigInfo? {
        return try {
            val res: GetNotificationConfigResponse = getNotificationConfig(client, GetNotificationConfigRequest(setOf(id)))
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
        val getNotificationConfigResponse: GetNotificationConfigResponse = NotificationsPluginInterface.suspendUntil {
            this.getNotificationConfig(
                client,
                getNotificationConfigRequest,
                it
            )
        }
        return getNotificationConfigResponse
    }

    private suspend fun createNotification(
        client: NodeClient,
        configInfo: NotificationConfigInfo?,
        reportDefinitionDetails: ReportDefinitionDetails,
        id: String,
        hits: Long?
    ) {
        val title: String = reportDefinitionDetails.reportDefinition.delivery!!.title
        val textMessage: String = reportDefinitionDetails.reportDefinition.delivery.textDescription
        val htmlMessage: String? = reportDefinitionDetails.reportDefinition.delivery.htmlDescription

        val urlDefinition: String = buildReportLink(reportDefinitionDetails.reportDefinition.source.origin, reportDefinitionDetails.tenant, id)

        val textWithURL: String = textMessage.replace("{{urlDefinition}}", urlDefinition).replace("{{hits}}", hits.toString())
        val htmlWithURL: String? = htmlMessage?.replace("{{urlDefinition}}", urlDefinition)?.replace("{{hits}}", hits.toString())

        log.info("esto es el mensaje html $htmlMessage")
        configInfo?.sendNotifications(
            client,
            title,
            textWithURL,
            htmlWithURL
        )
    }

    override fun runJob(job: ScheduledJobParameter, context: JobExecutionContext) {
        if (job !is ReportDefinitionDetails) {
            log.warn("$LOG_PREFIX:job is not of type ReportDefinitionDetails:${job.javaClass.name}")
            throw IllegalArgumentException("job is not of type ReportDefinitionDetails:${job.javaClass.name}")
        }
        scope.launch {
            val reportDefinitionDetails: ReportDefinitionDetails = job
            val currentTime = Instant.now()
            val endTime = context.expectedExecutionTime
            val beginTime = endTime.minus(reportDefinitionDetails.reportDefinition.format.duration)
            val reportInstance = ReportInstance(
                context.jobId,
                currentTime,
                currentTime,
                beginTime,
                endTime,
                job.tenant,
                job.access,
                reportDefinitionDetails,
                ReportInstance.Status.Success
            ) // TODO: Revert to Scheduled when background job execution supported
            val id = ReportInstancesIndex.createReportInstance(reportInstance)
            if (id == null) {
                log.warn("$LOG_PREFIX:runJob-job creation failed for $reportInstance")
            } else {
                log.info("$LOG_PREFIX:runJob-created job:$id")
                
                // Wazuh - Make queries
                val builderSearchResponse: SearchSourceBuilder = SearchSourceBuilder()
                    .query(
                        QueryBuilders.boolQuery()
                            .must(
                                QueryBuilders.rangeQuery("timestamp")
                                    .gt(beginTime)
                                    .lte(currentTime)
                            )
                            .must(
                                QueryBuilders.matchQuery("agent.id", "001")
                            )
                    )
                val jobSearchRequest: SearchRequest = SearchRequest().indices("wazuh-alerts-*").source(builderSearchResponse)
                val response: SearchResponse = nodeClient.search(jobSearchRequest).actionGet()

                val configInfo = getNotificationConfigInfo(
                    nodeClient,
                    id = reportDefinitionDetails.reportDefinition.delivery!!.configIds.get(0)
                )
                createNotification(nodeClient, configInfo, reportDefinitionDetails, id, response.getHits().getTotalHits()?.value)
            }
        }
    }
}


/**
 * Wazuh - Send notification
 */
suspend fun NotificationConfigInfo.sendNotifications(client: Client, title: String, compiledMessage: String, compiledMessageHTML: String?): String {
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
