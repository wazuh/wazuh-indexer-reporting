/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.reportsscheduler.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opensearch.cluster.service.ClusterService
import org.opensearch.commons.notifications.model.NotificationConfigInfo
import org.opensearch.jobscheduler.spi.JobExecutionContext
import org.opensearch.jobscheduler.spi.ScheduledJobParameter
import org.opensearch.jobscheduler.spi.ScheduledJobRunner
import org.opensearch.reportsscheduler.ReportsSchedulerPlugin.Companion.LOG_PREFIX
import org.opensearch.reportsscheduler.index.ReportInstancesIndex
import org.opensearch.reportsscheduler.model.ReportDefinitionDetails
import org.opensearch.reportsscheduler.model.ReportInstance
import org.opensearch.reportsscheduler.util.NotificationApiUtils.getNotificationConfigInfo
import org.opensearch.reportsscheduler.util.buildReportLink
import org.opensearch.reportsscheduler.util.logger
import org.opensearch.reportsscheduler.util.sendNotificationWithHTML
import org.opensearch.transport.client.Client
import org.opensearch.transport.client.node.NodeClient
import java.time.Instant

internal object ReportDefinitionJobRunner : ScheduledJobRunner {
    private val log by logger(ReportDefinitionJobRunner::class.java)
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var client: Client
    private lateinit var clusterService: ClusterService

    /**
     * Initialize the class
     * @param client The ES client
     * @param clusterService The ES cluster service
     */
    fun initialize(client: Client, clusterService: ClusterService) {
        this.client = client
        this.clusterService = clusterService
    }

    private suspend fun createNotification(
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
            this.client,
            title,
            body,
            htmlBody
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

                // -- Email delivery to every notification channel added to the report definition --
                for (notificationChannelId in reportDefinitionDetails.reportDefinition.delivery!!.configIds) {
                    val notificationChannel: NotificationConfigInfo? = getNotificationConfigInfo(
                        client as NodeClient,
                        notificationChannelId
                    )

                    if (notificationChannel != null) {
                        createNotification(
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
    }
}
