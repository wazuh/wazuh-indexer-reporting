/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.integTest.rest

import org.junit.Assert
import org.opensearch.core.rest.RestStatus
import org.opensearch.integTest.PluginRestTestCase
import org.opensearch.integTest.constructReportDefinitionRequest
import org.opensearch.reportsscheduler.ReportsSchedulerPlugin.Companion.BASE_REPORTS_URI
import org.opensearch.reportsscheduler.index.ResourceLockService
import org.opensearch.rest.RestRequest

/**
 * The resource-locks index must keep a copy on another node, or losing the node that holds its primary turns the
 * cluster red. `index.auto_expand_replicas: 0-1` gives no replica on a single node and one replica as soon as a
 * second node joins.
 */
class ResourceLocksIndexIT : PluginRestTestCase() {
    fun `test the resource locks index gets a replica as soon as a second node joins`() {
        // Creating a report definition takes the resource-creation lock, which creates the index.
        executeRequest(
            RestRequest.Method.POST.name,
            "$BASE_REPORTS_URI/definition",
            constructReportDefinitionRequest(),
            RestStatus.OK.status
        )
        val index = ResourceLockService.LOCKS_INDEX_NAME

        val settings = executeRequest(
            RestRequest.Method.GET.name,
            "/$index/_settings?flat_settings=true",
            "",
            RestStatus.OK.status
        ).getAsJsonObject(index).getAsJsonObject("settings")
        Assert.assertEquals("0-1", settings.get("index.auto_expand_replicas")?.asString)

        val numberOfNodes = System.getProperty("cluster.number_of_nodes", "1").toInt()
        val expectedReplicas = minOf(1, numberOfNodes - 1)
        val health = executeRequest(
            RestRequest.Method.GET.name,
            "/_cluster/health/$index?wait_for_status=green&timeout=30s",
            "",
            RestStatus.OK.status
        )
        Assert.assertEquals("green", health.get("status").asString)
        Assert.assertEquals(1 + expectedReplicas, health.get("active_shards").asInt)
    }
}
