/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.reportsscheduler.index

import org.opensearch.ResourceAlreadyExistsException
import org.opensearch.action.DocWriteRequest
import org.opensearch.action.admin.indices.create.CreateIndexRequest
import org.opensearch.action.delete.DeleteRequest
import org.opensearch.action.get.GetRequest
import org.opensearch.action.index.IndexRequest
import org.opensearch.action.support.WriteRequest
import org.opensearch.cluster.service.ClusterService
import org.opensearch.common.xcontent.XContentType
import org.opensearch.index.engine.VersionConflictEngineException
import org.opensearch.reportsscheduler.ReportsSchedulerPlugin.Companion.LOG_PREFIX
import org.opensearch.reportsscheduler.settings.PluginSettings
import org.opensearch.reportsscheduler.util.SecureIndexClient
import org.opensearch.reportsscheduler.util.logger
import org.opensearch.transport.client.Client
import java.io.IOException
import java.time.Instant

/**
 * Serializes the resource-creation-limit check-then-act sequence (count existing documents, then
 * create if under the configured max) with a short-lived mutex document per lock ID.
 *
 * The mutex is a document with a caller-supplied ID, created via [DocWriteRequest.OpType.CREATE]
 * so only one caller can hold it at a time for that ID. The resource count itself remains a live
 * search against the resource index; the lock only prevents two requests from evaluating that
 * count concurrently with a create.
 */
internal object ResourceLockService {
    private val log by logger(ResourceLockService::class.java)

    const val LOCKS_INDEX_NAME = ".opendistro-reports-resource-locks"
    private const val MAPPING_FILE_NAME = "resource-locks-mapping.yml"
    private const val SETTINGS_FILE_NAME = "resource-locks-settings.yml"
    private const val ACQUIRED_AT_FIELD = "acquired_at"
    private const val MAX_ACQUIRE_RETRIES = 20
    private const val ACQUIRE_RETRY_BACKOFF_MILLIS = 100L
    private const val STALE_THRESHOLD_MILLIS = 30_000L

    private lateinit var client: Client
    private lateinit var clusterService: ClusterService

    /**
     * Initialize the class
     * @param client The ES client
     * @param clusterService The ES cluster service
     */
    fun initialize(client: Client, clusterService: ClusterService) {
        ResourceLockService.client = SecureIndexClient(client)
        ResourceLockService.clusterService = clusterService
    }

    private fun isIndexExists(): Boolean {
        return clusterService.state().routingTable.hasIndex(LOCKS_INDEX_NAME)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun createIndex() {
        if (isIndexExists()) {
            return
        }
        val classLoader = ResourceLockService::class.java.classLoader
        val indexMappingSource = classLoader.getResource(MAPPING_FILE_NAME)?.readText()!!
        val indexSettingsSource = classLoader.getResource(SETTINGS_FILE_NAME)?.readText()!!
        val request = CreateIndexRequest(LOCKS_INDEX_NAME)
            .mapping(indexMappingSource, XContentType.YAML)
            .settings(indexSettingsSource, XContentType.YAML)
        try {
            client.threadPool().threadContext.stashContext().use {
                val response = client.admin().indices().create(request).actionGet(PluginSettings.operationTimeoutMs)
                if (response.isAcknowledged) {
                    log.info("$LOG_PREFIX:Index $LOCKS_INDEX_NAME creation Acknowledged")
                } else {
                    error("$LOG_PREFIX:Index $LOCKS_INDEX_NAME creation not Acknowledged")
                }
            }
        } catch (exception: ResourceAlreadyExistsException) {
            log.warn("message: ${exception.message}")
        } catch (exception: Exception) {
            if (exception.cause !is ResourceAlreadyExistsException) {
                throw exception
            }
        }
    }

    /**
     * Acquires the mutex for the given lock ID, blocking (with bounded retries) until it becomes
     * available.
     *
     * @param lockId the lock document ID.
     * @return the lock document ID, to be passed to [release].
     * @throws IOException if the lock could not be acquired after [MAX_ACQUIRE_RETRIES] attempts.
     */
    @Suppress("TooGenericExceptionCaught")
    fun acquire(lockId: String): String {
        createIndex()
        for (attempt in 1..MAX_ACQUIRE_RETRIES) {
            try {
                val request = IndexRequest(LOCKS_INDEX_NAME)
                    .id(lockId)
                    .source(mapOf(ACQUIRED_AT_FIELD to Instant.now().toEpochMilli()))
                    .opType(DocWriteRequest.OpType.CREATE)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                client.index(request).actionGet(PluginSettings.operationTimeoutMs)
                return lockId
            } catch (e: VersionConflictEngineException) {
                if (stealIfStale(lockId)) {
                    continue
                }
                backoff()
            }
        }
        throw IOException("Timed out waiting for the resource-creation lock on [$lockId].")
    }

    /**
     * Releases a previously acquired lock. Failures are logged and swallowed so a release problem
     * never surfaces as a resource-creation failure; a lock older than [STALE_THRESHOLD_MILLIS] is
     * stolen by the next caller regardless.
     *
     * @param lockId the lock document ID returned by [acquire].
     */
    fun release(lockId: String) {
        try {
            val request = DeleteRequest(LOCKS_INDEX_NAME, lockId)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            client.delete(request).actionGet(PluginSettings.operationTimeoutMs)
        } catch (e: Exception) {
            log.warn("Failed to release resource-creation lock [$lockId]: ${e.message}")
        }
    }

    /**
     * Deletes the lock document if it was acquired more than [STALE_THRESHOLD_MILLIS] ago,
     * guarding against a lock orphaned by a crashed node.
     *
     * @param lockId the lock document ID.
     * @return true if the stale lock was stolen (deleted) and the caller should retry immediately.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun stealIfStale(lockId: String): Boolean {
        return try {
            val response = client.get(GetRequest(LOCKS_INDEX_NAME, lockId)).actionGet(PluginSettings.operationTimeoutMs)
            if (!response.isExists) {
                // Released concurrently between our failed acquire and this check; retry immediately.
                return true
            }
            val acquiredAt = response.sourceAsMap?.get(ACQUIRED_AT_FIELD)
            val acquiredAtMillis = if (acquiredAt is Number) acquiredAt.toLong() else 0L
            if (Instant.now().toEpochMilli() - acquiredAtMillis <= STALE_THRESHOLD_MILLIS) {
                return false
            }
            log.warn("Stealing stale resource-creation lock [$lockId].")
            val deleteRequest = DeleteRequest(LOCKS_INDEX_NAME, lockId)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            client.delete(deleteRequest).actionGet(PluginSettings.operationTimeoutMs)
            true
        } catch (e: Exception) {
            log.warn("Failed to check staleness of resource-creation lock [$lockId]: ${e.message}")
            false
        }
    }

    private fun backoff() {
        Thread.sleep(ACQUIRE_RETRY_BACKOFF_MILLIS)
    }
}
